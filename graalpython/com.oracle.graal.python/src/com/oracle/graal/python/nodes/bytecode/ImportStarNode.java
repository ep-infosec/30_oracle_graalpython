/*
 * Copyright (c) 2017, 2022, Oracle and/or its affiliates.
 * Copyright (c) 2013, Regents of the University of California
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are
 * permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this list of
 * conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of
 * conditions and the following disclaimer in the documentation and/or other materials provided
 * with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS
 * OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
 * COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE
 * GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED
 * AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.oracle.graal.python.nodes.bytecode;

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.TypeError;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.T___ALL__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.T___DICT__;
import static com.oracle.graal.python.nodes.frame.ReadLocalsNode.fastGetCustomLocalsOrGlobals;
import static com.oracle.graal.python.util.PythonUtils.TS_ENCODING;

import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.builtins.objects.function.PArguments;
import com.oracle.graal.python.builtins.objects.mappingproxy.PMappingproxy;
import com.oracle.graal.python.builtins.objects.module.PythonModule;
import com.oracle.graal.python.builtins.objects.object.PythonObject;
import com.oracle.graal.python.lib.GetNextNode;
import com.oracle.graal.python.lib.PyObjectGetAttr;
import com.oracle.graal.python.lib.PyObjectGetItem;
import com.oracle.graal.python.lib.PyObjectGetIter;
import com.oracle.graal.python.lib.PyObjectSetAttr;
import com.oracle.graal.python.lib.PyObjectSetItem;
import com.oracle.graal.python.lib.PyObjectSizeNode;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.interop.PForeignToPTypeNode;
import com.oracle.graal.python.nodes.object.GetOrCreateDictNode;
import com.oracle.graal.python.nodes.object.IsBuiltinClassProfile;
import com.oracle.graal.python.nodes.statement.AbstractImportNode;
import com.oracle.graal.python.nodes.util.CannotCastException;
import com.oracle.graal.python.nodes.util.CastToTruffleStringNode;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.InvalidArrayIndexException;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.profiles.ConditionProfile;
import com.oracle.truffle.api.strings.TruffleString;

@GenerateUncached
public abstract class ImportStarNode extends AbstractImportNode {
    public abstract void execute(VirtualFrame frame, TruffleString moduleName, int level);

    @Specialization
    void doImport(VirtualFrame frame, TruffleString moduleName, int level,
                    @Cached ImportName importNameNode,
                    @Cached PyObjectSetItem setItemNode,
                    @Cached PyObjectSetAttr setAttrNode,
                    @Cached GetOrCreateDictNode getDictNode,
                    @Cached PyObjectGetAttr getAttrNode,
                    @Cached PyObjectGetIter getIterNode,
                    @Cached GetNextNode getNextNode,
                    @Cached PyObjectSizeNode sizeNode,
                    @Cached PyObjectGetItem getItemNode,
                    @Cached ConditionProfile javaImport,
                    @Cached ConditionProfile havePyFrame,
                    @Cached ConditionProfile haveCustomLocals,
                    @Cached CastToTruffleStringNode castToTruffleStringNode,
                    @Cached TruffleString.CodePointLengthNode codePointLengthNode,
                    @Cached TruffleString.CodePointAtIndexNode codePointAtIndexNode,
                    @Cached IsBuiltinClassProfile isAttributeErrorProfile,
                    @Cached IsBuiltinClassProfile isStopIterationProfile) {
        Object importedModule = importModule(frame, moduleName, PArguments.getGlobals(frame), T_IMPORT_ALL, level, importNameNode);
        PythonObject locals = fastGetCustomLocalsOrGlobals(frame, havePyFrame, haveCustomLocals);

        if (javaImport.profile(emulateJython() && getContext().getEnv().isHostObject(importedModule))) {
            try {
                InteropLibrary interopLib = InteropLibrary.getFactory().getUncached();
                Object hostAttrs = interopLib.getMembers(importedModule, true);
                int len = (int) interopLib.getArraySize(hostAttrs);
                for (int i = 0; i < len; i++) {
                    // interop protocol guarantees these are strings
                    String attrName = interopLib.asString(interopLib.readArrayElement(hostAttrs, i));
                    Object attr = interopLib.readMember(importedModule, attrName);
                    attr = PForeignToPTypeNode.getUncached().executeConvert(attr);
                    writeAttribute(frame, locals, TruffleString.fromJavaStringUncached(attrName, TS_ENCODING), attr, setItemNode, setAttrNode);
                }
            } catch (UnknownIdentifierException | UnsupportedMessageException | InvalidArrayIndexException e) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw new IllegalStateException(e);
            }
        } else {
            try {
                Object attrAll = getAttrNode.execute(frame, importedModule, T___ALL__);
                int n = sizeNode.execute(frame, attrAll);
                for (int i = 0; i < n; i++) {
                    Object attrName = getItemNode.execute(frame, attrAll, i);
                    writeAttributeToLocals(frame, moduleName, (PythonModule) importedModule, locals, attrName, true, castToTruffleStringNode, codePointLengthNode,
                                    codePointAtIndexNode, getAttrNode, setItemNode, setAttrNode);
                }
            } catch (PException e) {
                e.expectAttributeError(isAttributeErrorProfile);
                assert importedModule instanceof PythonModule;
                Object keysIterator = getIterNode.execute(frame, getDictNode.execute(importedModule));
                while (true) {
                    try {
                        Object key = getNextNode.execute(frame, keysIterator);
                        writeAttributeToLocals(frame, moduleName, (PythonModule) importedModule, locals, key, false, castToTruffleStringNode, codePointLengthNode,
                                        codePointAtIndexNode, getAttrNode, setItemNode, setAttrNode);
                    } catch (PException iterException) {
                        iterException.expectStopIteration(isStopIterationProfile);
                        break;
                    }
                }
            }
        }
    }

    // TODO: remove once we removed PythonModule globals
    private void writeAttribute(VirtualFrame frame, PythonObject globals, TruffleString name, Object value, PyObjectSetItem setItemNode, PyObjectSetAttr setAttrNode) {
        if (globals instanceof PDict || globals instanceof PMappingproxy) {
            setItemNode.execute(frame, globals, name, value);
        } else {
            setAttrNode.execute(frame, globals, name, value);
        }
    }

    private void writeAttributeToLocals(VirtualFrame frame, TruffleString moduleName, PythonModule importedModule, PythonObject locals, Object attrName, boolean fromAll,
                    CastToTruffleStringNode castToTruffleStringNode, TruffleString.CodePointLengthNode cpLenNode, TruffleString.CodePointAtIndexNode cpAtIndexNode, PyObjectGetAttr getAttr,
                    PyObjectSetItem dictWriteNode, PyObjectSetAttr setAttrNode) {
        try {
            TruffleString name = castToTruffleStringNode.execute(attrName);
            // skip attributes with leading '__' if there was no '__all__' attribute (see
            // 'ceval.c: import_all_from')
            if (fromAll || !isDunder(name, cpLenNode, cpAtIndexNode)) {
                Object moduleAttr = getAttr.execute(frame, importedModule, name);
                writeAttribute(frame, locals, name, moduleAttr, dictWriteNode, setAttrNode);
            }
        } catch (CannotCastException cce) {
            throw PRaiseNode.raiseUncached(this, TypeError, fromAll ? ErrorMessages.ITEM_IN_S_MUST_BE_STRING : ErrorMessages.KEY_IN_S_MUST_BE_STRING,
                            moduleName, fromAll ? T___ALL__ : T___DICT__, attrName);
        }
    }

    private boolean isDunder(TruffleString s, TruffleString.CodePointLengthNode cpLenNode, TruffleString.CodePointAtIndexNode cpAtIndexNode) {
        return cpLenNode.execute(s, TS_ENCODING) >= 2 && cpAtIndexNode.execute(s, 0, TS_ENCODING) == '_' && cpAtIndexNode.execute(s, 1, TS_ENCODING) == '_';
    }

    public static ImportStarNode create() {
        return ImportStarNodeGen.create();
    }

    public static ImportStarNode getUncached() {
        return ImportStarNodeGen.getUncached();
    }
}
