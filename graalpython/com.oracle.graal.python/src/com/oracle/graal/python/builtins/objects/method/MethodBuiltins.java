/*
 * Copyright (c) 2017, 2022, Oracle and/or its affiliates.
 * Copyright (c) 2014, Regents of the University of California
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

package com.oracle.graal.python.builtins.objects.method;

import static com.oracle.graal.python.nodes.SpecialAttributeNames.J___CODE__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.J___DEFAULTS__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.J___DICT__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.J___FUNC__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.J___KWDEFAULTS__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.T___CODE__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.T___NAME__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.T___QUALNAME__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___GETATTRIBUTE__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___GET__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___REPR__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___GETATTRIBUTE__;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.TypeError;

import java.util.List;

import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.function.PKeyword;
import com.oracle.graal.python.builtins.objects.object.ObjectBuiltins;
import com.oracle.graal.python.builtins.objects.str.StringUtils.SimpleTruffleStringFormatNode;
import com.oracle.graal.python.lib.PyObjectLookupAttr;
import com.oracle.graal.python.lib.PyObjectReprAsTruffleStringNode;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.builtins.FunctionNodes.GetDefaultsNode;
import com.oracle.graal.python.nodes.builtins.FunctionNodes.GetKeywordDefaultsNode;
import com.oracle.graal.python.nodes.call.special.LookupAndCallBinaryNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonTernaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.object.GetOrCreateDictNode;
import com.oracle.graal.python.nodes.object.IsBuiltinClassProfile;
import com.oracle.graal.python.nodes.util.CannotCastException;
import com.oracle.graal.python.nodes.util.CastToTruffleStringNode;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.strings.TruffleString;

@CoreFunctions(extendClasses = PythonBuiltinClassType.PMethod)
public class MethodBuiltins extends PythonBuiltins {

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return MethodBuiltinsFactory.getFactories();
    }

    @Builtin(name = J___FUNC__, minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    public abstract static class FuncNode extends PythonBuiltinNode {
        @Specialization
        protected Object doIt(PMethod self) {
            return self.getFunction();
        }
    }

    @Builtin(name = J___CODE__, minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    public abstract static class CodeNode extends PythonBuiltinNode {
        @Specialization
        protected Object doIt(VirtualFrame frame, PMethod self,
                        @Cached("create(GetAttribute)") LookupAndCallBinaryNode getCode) {
            return getCode.executeObject(frame, self.getFunction(), T___CODE__);
        }
    }

    @Builtin(name = J___DICT__, minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    public abstract static class DictNode extends PythonBuiltinNode {
        @Specialization
        protected Object doIt(PMethod self,
                        @Cached GetOrCreateDictNode getDict) {
            return getDict.execute(self.getFunction());
        }
    }

    @ImportStatic(PGuards.class)
    @Builtin(name = J___GETATTRIBUTE__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class GetattributeNode extends PythonBuiltinNode {
        @Specialization
        protected Object doIt(VirtualFrame frame, PMethod self, Object keyObj,
                        @Cached ObjectBuiltins.GetAttributeNode objectGetattrNode,
                        @Cached IsBuiltinClassProfile errorProfile,
                        @Cached CastToTruffleStringNode castKeyToStringNode) {
            TruffleString key;
            try {
                key = castKeyToStringNode.execute(keyObj);
            } catch (CannotCastException e) {
                throw raise(TypeError, ErrorMessages.ATTR_NAME_MUST_BE_STRING, keyObj);
            }

            try {
                return objectGetattrNode.execute(frame, self, key);
            } catch (PException e) {
                e.expectAttributeError(errorProfile);
                return objectGetattrNode.execute(frame, self.getFunction(), key);
            }
        }

        @Specialization(guards = "!isPMethod(self)")
        Object getattribute(Object self, @SuppressWarnings("unused") Object key) {
            throw raise(TypeError, ErrorMessages.DESCRIPTOR_REQUIRES_OBJ, T___GETATTRIBUTE__, "method", self);
        }
    }

    @Builtin(name = J___REPR__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class ReprNode extends PythonUnaryBuiltinNode {
        @Specialization
        static TruffleString reprMethod(VirtualFrame frame, PMethod method,
                        @Cached PyObjectReprAsTruffleStringNode repr,
                        @Cached CastToTruffleStringNode toStringNode,
                        @Cached PyObjectLookupAttr lookup,
                        @Cached SimpleTruffleStringFormatNode simpleTruffleStringFormatNode) {
            Object self = method.getSelf();
            Object func = method.getFunction();

            Object funcName = lookup.execute(frame, func, T___QUALNAME__);
            if (funcName == PNone.NO_VALUE) {
                funcName = lookup.execute(frame, func, T___NAME__);
            }

            try {
                return simpleTruffleStringFormatNode.format("<bound method %s of %s>", toStringNode.execute(funcName), repr.execute(frame, self));
            } catch (CannotCastException e) {
                return simpleTruffleStringFormatNode.format("<bound method ? of %s>", repr.execute(frame, self));
            }
        }
    }

    @Builtin(name = J___DEFAULTS__, minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    public abstract static class GetMethodDefaultsNode extends PythonUnaryBuiltinNode {
        @Specialization
        Object defaults(PMethod self,
                        @Cached GetDefaultsNode getDefaultsNode) {
            Object[] argDefaults = getDefaultsNode.execute(self);
            assert argDefaults != null;
            return (argDefaults.length == 0) ? PNone.NONE : factory().createTuple(argDefaults);
        }
    }

    @Builtin(name = J___KWDEFAULTS__, minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    public abstract static class GetMethodKwdefaultsNode extends PythonUnaryBuiltinNode {
        @Specialization
        Object kwDefaults(PMethod self,
                        @Cached GetKeywordDefaultsNode getKeywordDefaultsNode) {
            PKeyword[] kwdefaults = getKeywordDefaultsNode.execute(self);
            return (kwdefaults.length > 0) ? factory().createDict(kwdefaults) : PNone.NONE;
        }
    }

    @Builtin(name = J___GET__, minNumOfPositionalArgs = 2, maxNumOfPositionalArgs = 3)
    @GenerateNodeFactory
    public abstract static class GetNode extends PythonTernaryBuiltinNode {
        @Specialization
        PMethod doGeneric(@SuppressWarnings("unused") PMethod self, Object obj, @SuppressWarnings("unused") Object cls) {
            if (self.getSelf() != null) {
                return self;
            }
            return factory().createMethod(obj, self.getFunction());
        }
    }
}
