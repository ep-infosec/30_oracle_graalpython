/*
 * Copyright (c) 2021, 2022, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.oracle.graal.python.builtins.modules.cext;

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.SystemError;
import static com.oracle.graal.python.nodes.ErrorMessages.S_NEEDS_S_AS_FIRST_ARG;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.T___DOC__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.T___NAME__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.T___PACKAGE__;
import static com.oracle.graal.python.util.PythonUtils.TS_ENCODING;

import java.util.List;

import com.oracle.graal.python.annotations.ArgumentClinic;
import com.oracle.graal.python.annotations.ArgumentClinic.ClinicConversion;
import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.Python3Core;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.cext.capi.CApiContext;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes.PRaiseNativeNode;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes.TransformExceptionToNativeNode;
import com.oracle.graal.python.builtins.objects.module.PythonModule;
import com.oracle.graal.python.builtins.objects.object.ObjectBuiltins;
import com.oracle.graal.python.builtins.objects.str.PString;
import com.oracle.graal.python.builtins.objects.str.StringBuiltins.EndsWithNode;
import com.oracle.graal.python.lib.PyObjectLookupAttr;
import com.oracle.graal.python.nodes.attributes.WriteAttributeToObjectNode;
import com.oracle.graal.python.nodes.call.CallNode;
import com.oracle.graal.python.nodes.classes.IsSubtypeNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonTernaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryClinicBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.clinic.ArgumentClinicProvider;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.nodes.util.CastToTruffleStringNode;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.strings.TruffleString;

@CoreFunctions(extendsModule = PythonCextBuiltins.PYTHON_CEXT)
@GenerateNodeFactory
public final class PythonCextModuleBuiltins extends PythonBuiltins {

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return PythonCextModuleBuiltinsFactory.getFactories();
    }

    @Override
    public void initialize(Python3Core core) {
        super.initialize(core);
    }

    @Builtin(name = "_PyModule_GetAndIncMaxModuleNumber")
    @GenerateNodeFactory
    abstract static class PyModuleGetAndIncMaxModuleNumber extends PythonBuiltinNode {

        @Specialization
        long doIt() {
            CApiContext nativeContext = getContext().getCApiContext();
            return nativeContext.getAndIncMaxModuleNumber();
        }
    }

    @Builtin(name = "PyModule_SetDocString", minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class SetDocStringNode extends PythonBinaryBuiltinNode {
        @Specialization
        static Object run(VirtualFrame frame, PythonModule module, Object doc,
                        @Cached ObjectBuiltins.SetattrNode setattrNode) {
            setattrNode.execute(frame, module, T___DOC__, doc);
            return PNone.NONE;
        }
    }

    @Builtin(name = "PyModule_NewObject", minNumOfPositionalArgs = 1, parameterNames = {"name"})
    @ArgumentClinic(name = "name", conversion = ClinicConversion.TString)
    @GenerateNodeFactory
    public abstract static class PyModuleNewObjectNode extends PythonUnaryClinicBuiltinNode {

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return PythonCextModuleBuiltinsClinicProviders.PyModuleNewObjectNodeClinicProviderGen.INSTANCE;
        }

        @Specialization
        static Object run(VirtualFrame frame, TruffleString name,
                        @Cached CallNode callNode) {
            return callNode.execute(frame, PythonBuiltinClassType.PythonModule, new Object[]{name});
        }
    }

    @Builtin(name = "_PyModule_CreateInitialized_PyModule_New", parameterNames = {"name"})
    @ArgumentClinic(name = "name", conversion = ClinicConversion.TString)
    @GenerateNodeFactory
    public abstract static class PyModuleCreateInitializedNewNode extends PythonUnaryClinicBuiltinNode {

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return PythonCextModuleBuiltinsClinicProviders.PyModuleCreateInitializedNewNodeClinicProviderGen.INSTANCE;
        }

        @Specialization
        Object run(VirtualFrame frame, TruffleString name,
                        @Cached CallNode callNode,
                        @Cached ObjectBuiltins.SetattrNode setattrNode,
                        @Cached TruffleString.CodePointLengthNode codePointLengthNode,
                        @Cached EndsWithNode endsWithNode,
                        @Cached TruffleString.LastIndexOfCodePointNode lastIndexNode,
                        @Cached TruffleString.SubstringNode substringNode) {
            // see CPython's Objects/moduleobject.c - _PyModule_CreateInitialized for
            // comparison how they handle _Py_PackageContext
            TruffleString newModuleName = name;
            PythonContext ctx = getContext();
            TruffleString pyPackageContext = ctx.getPyPackageContext() == null ? null : ctx.getPyPackageContext();
            if (pyPackageContext != null && endsWithNode.executeBoolean(frame, pyPackageContext, newModuleName, 0, codePointLengthNode.execute(pyPackageContext, TS_ENCODING))) {
                newModuleName = pyPackageContext;
                ctx.setPyPackageContext(null);
            }
            Object newModule = callNode.execute(frame, PythonBuiltinClassType.PythonModule, new Object[]{newModuleName});
            // TODO: (tfel) I don't think this is the right place to set it, but somehow
            // at least in the import of sklearn.neighbors.dist_metrics through
            // sklearn.neighbors.ball_tree the __package__ attribute seems to be already
            // set in CPython. To not produce a warning, I'm setting it here, although I
            // could not find what CPython really does
            int nameLength = codePointLengthNode.execute(newModuleName, TS_ENCODING);
            int idx = lastIndexNode.execute(newModuleName, '.', nameLength, 0, TS_ENCODING);
            if (idx > -1) {
                setattrNode.execute(frame, newModule, T___PACKAGE__, substringNode.execute(newModuleName, 0, idx, TS_ENCODING, false));
            }
            return newModule;
        }
    }

    @Builtin(name = "PyModule_GetNameObject", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class PyModuleGetNameObjectNode extends PythonUnaryBuiltinNode {
        @Specialization
        Object getName(VirtualFrame frame, Object o,
                        @Cached PyObjectLookupAttr lookupAttrNode,
                        @Cached TransformExceptionToNativeNode transformExceptionToNativeNode) {
            try {
                return lookupAttrNode.execute(frame, o, T___NAME__);
            } catch (PException e) {
                transformExceptionToNativeNode.execute(e);
                return getContext().getNativeNull();
            }
        }
    }

    @Builtin(name = "PyModule_AddObjectRef", minNumOfPositionalArgs = 3)
    @GenerateNodeFactory
    public abstract static class PyModuleAddObjectNode extends PythonTernaryBuiltinNode {
        @Specialization(guards = "isModuleSubtype(frame, m, getClassNode, isSubtypeNode)")
        static Object addObject(@SuppressWarnings("unused") VirtualFrame frame, Object m, TruffleString k, Object o,
                        @SuppressWarnings("unused") @Cached GetClassNode getClassNode,
                        @SuppressWarnings("unused") @Cached IsSubtypeNode isSubtypeNode,
                        @Cached WriteAttributeToObjectNode writeAtrrNode,
                        @Cached TransformExceptionToNativeNode transformExceptionToNativeNode) {
            try {
                writeAtrrNode.execute(m, k, o);
                return 0;
            } catch (PException e) {
                transformExceptionToNativeNode.execute(e);
                return -1;
            }
        }

        @Specialization(guards = "isModuleSubtype(frame, m, getClassNode, isSubtypeNode)")
        Object addObject(VirtualFrame frame, Object m, PString k, Object o,
                        @Cached CastToTruffleStringNode castToStringNode,
                        @SuppressWarnings("unused") @Cached GetClassNode getClassNode,
                        @SuppressWarnings("unused") @Cached IsSubtypeNode isSubtypeNode,
                        @Cached WriteAttributeToObjectNode writeAtrrNode,
                        @Cached TransformExceptionToNativeNode transformExceptionToNativeNode) {
            return addObject(frame, m, castToStringNode.execute(k), o, getClassNode, isSubtypeNode, writeAtrrNode, transformExceptionToNativeNode);
        }

        @SuppressWarnings("unused")
        @Specialization(guards = "!isModuleSubtype(frame, m, getClassNode, isSubtypeNode)")
        public static Object pop(VirtualFrame frame, Object m, Object key, Object defaultValue,
                        @SuppressWarnings("unused") @Cached GetClassNode getClassNode,
                        @SuppressWarnings("unused") @Cached IsSubtypeNode isSubtypeNode,
                        @Cached PRaiseNativeNode raiseNativeNode) {
            return raiseNativeNode.raiseInt(frame, -1, SystemError, S_NEEDS_S_AS_FIRST_ARG, "PyModule_AddObjectRef", "module");
        }

        protected boolean isModuleSubtype(VirtualFrame frame, Object obj, GetClassNode getClassNode, IsSubtypeNode isSubtypeNode) {
            return isSubtypeNode.execute(frame, getClassNode.execute(obj), PythonBuiltinClassType.PythonModule);
        }
    }

}
