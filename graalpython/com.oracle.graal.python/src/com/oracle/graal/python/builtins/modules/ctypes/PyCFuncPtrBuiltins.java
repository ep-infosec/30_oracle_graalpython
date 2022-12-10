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
package com.oracle.graal.python.builtins.modules.ctypes;

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.RuntimeError;
import static com.oracle.graal.python.builtins.modules.ctypes.CDataTypeBuiltins.GenericPyCDataNew;
import static com.oracle.graal.python.builtins.modules.ctypes.CDataTypeBuiltins.T__HANDLE;
import static com.oracle.graal.python.builtins.modules.ctypes.CtypesModuleBuiltins.FUNCFLAG_CDECL;
import static com.oracle.graal.python.builtins.modules.ctypes.CtypesModuleBuiltins.getFunctionFromLongObject;
import static com.oracle.graal.python.builtins.modules.ctypes.CtypesModuleBuiltins.getHandleFromLongObject;
import static com.oracle.graal.python.builtins.modules.ctypes.PyCFuncPtrTypeBuiltins.PARAMFLAG_FIN;
import static com.oracle.graal.python.builtins.modules.ctypes.PyCFuncPtrTypeBuiltins.PARAMFLAG_FLCID;
import static com.oracle.graal.python.builtins.modules.ctypes.PyCFuncPtrTypeBuiltins.PARAMFLAG_FOUT;
import static com.oracle.graal.python.builtins.modules.ctypes.PyCFuncPtrTypeBuiltins.T___ctypes_from_outparam__;
import static com.oracle.graal.python.builtins.modules.ctypes.PyCFuncPtrTypeBuiltins.PyCFuncPtrTypeNewNode.converters_from_argtypes;
import static com.oracle.graal.python.nodes.BuiltinNames.T__CTYPES;
import static com.oracle.graal.python.nodes.ErrorMessages.ARGTYPES_MUST_BE_A_SEQUENCE_OF_TYPES;
import static com.oracle.graal.python.nodes.ErrorMessages.ARGUMENT_MUST_BE_CALLABLE_OR_INTEGER_FUNCTION_ADDRESS;
import static com.oracle.graal.python.nodes.ErrorMessages.CALL_TAKES_EXACTLY_D_ARGUMENTS_D_GIVEN;
import static com.oracle.graal.python.nodes.ErrorMessages.CANNOT_CONSTRUCT_INSTANCE_OF_THIS_CLASS_NO_ARGTYPES;
import static com.oracle.graal.python.nodes.ErrorMessages.NOT_ENOUGH_ARGUMENTS;
import static com.oracle.graal.python.nodes.ErrorMessages.NULL_STGDICT_UNEXPECTED;
import static com.oracle.graal.python.nodes.ErrorMessages.OUT_PARAMETER_D_MUST_BE_A_POINTER_TYPE_NOT_S;
import static com.oracle.graal.python.nodes.ErrorMessages.PARAMFLAGS_MUST_BE_A_SEQUENCE_OF_INT_STRING_VALUE_TUPLES;
import static com.oracle.graal.python.nodes.ErrorMessages.PARAMFLAGS_MUST_BE_A_TUPLE_OR_NONE;
import static com.oracle.graal.python.nodes.ErrorMessages.PARAMFLAGS_MUST_HAVE_THE_SAME_LENGTH_AS_ARGTYPES;
import static com.oracle.graal.python.nodes.ErrorMessages.PARAMFLAG_D_NOT_YET_IMPLEMENTED;
import static com.oracle.graal.python.nodes.ErrorMessages.PARAMFLAG_VALUE_D_NOT_SUPPORTED;
import static com.oracle.graal.python.nodes.ErrorMessages.REQUIRED_ARGUMENT_S_MISSING;
import static com.oracle.graal.python.nodes.ErrorMessages.RESTYPE_MUST_BE_A_TYPE_A_CALLABLE_OR_NONE;
import static com.oracle.graal.python.nodes.ErrorMessages.S_OUT_PARAMETER_MUST_BE_PASSED_AS_DEFAULT_VALUE;
import static com.oracle.graal.python.nodes.ErrorMessages.THE_ERRCHECK_ATTRIBUTE_MUST_BE_CALLABLE;
import static com.oracle.graal.python.nodes.ErrorMessages.THE_HANDLE_ATTRIBUTE_OF_THE_SECOND_ARGUMENT_MUST_BE_AN_INTEGER;
import static com.oracle.graal.python.nodes.ErrorMessages.THIS_FUNCTION_TAKES_AT_LEAST_D_ARGUMENT_S_D_GIVEN;
import static com.oracle.graal.python.nodes.ErrorMessages.THIS_FUNCTION_TAKES_D_ARGUMENT_S_D_GIVEN;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___CALL__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___NEW__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___REPR__;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.AttributeError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.NotImplementedError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.TypeError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.ValueError;
import static com.oracle.graal.python.util.PythonUtils.TS_ENCODING;
import static com.oracle.graal.python.util.PythonUtils.toTruffleStringUncached;

import java.util.List;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.modules.SysModuleBuiltins.AuditNode;
import com.oracle.graal.python.builtins.modules.cext.PythonCextLongBuiltins.PyLongAsVoidPtr;
import com.oracle.graal.python.builtins.modules.ctypes.CDataTypeBuiltins.KeepRefNode;
import com.oracle.graal.python.builtins.modules.ctypes.CtypesModuleBuiltins.CallProcNode;
import com.oracle.graal.python.builtins.modules.ctypes.CtypesModuleBuiltins.CtypesDlSymNode;
import com.oracle.graal.python.builtins.modules.ctypes.CtypesModuleBuiltins.CtypesThreadState;
import com.oracle.graal.python.builtins.modules.ctypes.CtypesModuleBuiltins.DLHandler;
import com.oracle.graal.python.builtins.modules.ctypes.CtypesModuleBuiltins.NativeFunction;
import com.oracle.graal.python.builtins.modules.ctypes.CtypesNodes.PyTypeCheck;
import com.oracle.graal.python.builtins.modules.ctypes.StgDictBuiltins.PyObjectStgDictNode;
import com.oracle.graal.python.builtins.modules.ctypes.StgDictBuiltins.PyTypeStgDictNode;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.cext.PythonNativeVoidPtr;
import com.oracle.graal.python.builtins.objects.common.KeywordsStorage;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes.GetInternalObjectArrayNode;
import com.oracle.graal.python.builtins.objects.function.PKeyword;
import com.oracle.graal.python.builtins.objects.list.PList;
import com.oracle.graal.python.builtins.objects.str.StringUtils.SimpleTruffleStringFormatNode;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.builtins.objects.type.TypeNodes.GetNameNode;
import com.oracle.graal.python.lib.PyCallableCheckNode;
import com.oracle.graal.python.lib.PyLongCheckNode;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.PNodeWithRaise;
import com.oracle.graal.python.nodes.attributes.GetAttributeNode.GetAnyAttributeNode;
import com.oracle.graal.python.nodes.attributes.LookupAttributeInMRONode;
import com.oracle.graal.python.nodes.call.CallNode;
import com.oracle.graal.python.nodes.call.special.LookupAndCallUnaryNode.LookupAndCallUnaryDynamicNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonVarargsBuiltinNode;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.nodes.util.CastToJavaIntExactNode;
import com.oracle.graal.python.nodes.util.CastToTruffleStringNode;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.strings.TruffleString;

@CoreFunctions(extendClasses = PythonBuiltinClassType.PyCFuncPtr)
public class PyCFuncPtrBuiltins extends PythonBuiltins {

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return PyCFuncPtrBuiltinsFactory.getFactories();
    }

    /*
     * PyCFuncPtr_new accepts different argument lists in addition to the standard _basespec_
     * keyword arg:
     *
     * one argument form "i" - function address "O" - must be a callable, creates a C callable
     * function
     *
     * two or more argument forms (the third argument is a paramflags tuple) "(sO)|..." - (function
     * name, dll object (with an integer handle)), paramflags "(iO)|..." - (function ordinal, dll
     * object (with an integer handle)), paramflags "is|..." - vtable index, method name, creates
     * callable calling COM vtbl
     */
    @Builtin(name = J___NEW__, minNumOfPositionalArgs = 1, takesVarArgs = true, takesVarKeywordArgs = true)
    @GenerateNodeFactory
    protected abstract static class PyCFuncPtrNewNode extends PythonBuiltinNode {

        protected static boolean isLong(Object[] arg, PyLongCheckNode longCheckNode) {
            return arg[0] instanceof PythonNativeVoidPtr || longCheckNode.execute(arg[0]);
        }

        protected static boolean isTuple(Object[] arg) {
            return PGuards.isPTuple(arg[0]);
        }

        @Specialization(guards = "args.length == 0")
        Object simple(Object type, @SuppressWarnings("unused") Object[] args, @SuppressWarnings("unused") PKeyword[] kwds,
                        @Cached PyTypeStgDictNode pyTypeStgDictNode) {
            CDataObject ob = factory().createPyCFuncPtrObject(type);
            StgDictObject dict = pyTypeStgDictNode.checkAbstractClass(type, getRaiseNode());
            return GenericPyCDataNew(dict, ob);
        }

        @Specialization(guards = {"args.length <= 1", "isTuple(args)"})
        Object fromNativeLibrary(VirtualFrame frame, Object type, Object[] args, @SuppressWarnings("unused") PKeyword[] kwds,
                        @Cached PyCFuncPtrFromDllNode pyCFuncPtrFromDllNode) {
            return pyCFuncPtrFromDllNode.execute(frame, type, args, getContext(), factory());
        }

        @Specialization(guards = {"args.length == 1", "isLong(args, longCheckNode)"})
        Object usingNativePointer(Object type, Object[] args, @SuppressWarnings("unused") PKeyword[] kwds,
                        @SuppressWarnings("unused") @Cached PyLongCheckNode longCheckNode,
                        @Cached PyLongAsVoidPtr asVoidPtr,
                        @Cached PyTypeStgDictNode pyTypeStgDictNode) {
            Object ptr = asVoidPtr.execute(args[0]);
            CDataObject ob = factory().createPyCFuncPtrObject(type);
            StgDictObject dict = pyTypeStgDictNode.checkAbstractClass(type, getRaiseNode());
            GenericPyCDataNew(dict, ob);
            ob.b_ptr = PtrValue.create(dict.ffi_type_pointer, dict.size, ptr, 0);
            return ob;
        }

        @Specialization(guards = {"args.length > 0", "!isPTuple(args)", "!isLong(args, longCheckNode)"})
        Object callback(Object type, Object[] args, @SuppressWarnings("unused") PKeyword[] kwds,
                        @SuppressWarnings("unused") @Cached PyLongCheckNode longCheckNode,
                        // @Cached KeepRefNode keepRefNode,
                        @Cached PyCallableCheckNode callableCheck,
                        @Cached PyTypeStgDictNode pyTypeStgDictNode) {
            Object callable = args[0];
            if (!callableCheck.execute(callable)) {
                throw raise(TypeError, ARGUMENT_MUST_BE_CALLABLE_OR_INTEGER_FUNCTION_ADDRESS);
            }

            /*
             * XXX XXX This would allow passing additional options. For COM method
             * *implementations*, we would probably want different behaviour than in 'normal'
             * callback functions: return a HRESULT if an exception occurs in the callback, and
             * print the traceback not only on the console, but also to OutputDebugString() or
             * something like that.
             */
            /*
             * if (kwds && _PyDict_GetItemIdWithError(kwds, &PyId_options)) { ... } else if
             * (PyErr_Occurred()) { return NULL; }
             */

            StgDictObject dict = pyTypeStgDictNode.checkAbstractClass(type, getRaiseNode());
            /* XXXX Fails if we do: 'PyCFuncPtr(lambda x: x)' */
            if (dict == null || dict.argtypes == null) {
                throw raise(TypeError, CANNOT_CONSTRUCT_INSTANCE_OF_THIS_CLASS_NO_ARGTYPES);
            }

            throw raise(NotImplementedError, toTruffleStringUncached("callbacks aren't supported yet."));
            /*-
            CThunkObject thunk = _ctypes_alloc_callback(callable, dict.argtypes, dict.restype, dict.flags);
            PyCFuncPtrObject self = factory().createPyCFuncPtrObject(type);
            GenericPyCDataNew(dict, self);
            self.callable = callable;
            self.thunk = thunk;
            self.b_ptr = PtrValue.object(thunk.pcl_exec);
            
            keepRefNode.execute(self, 0, thunk, factory());
            return self;
            */
        }

        @Specialization(guards = {"args.length != 1", "!isPTuple(args)", "isLong(args, longCheckNode)"})
        Object error(@SuppressWarnings("unused") Object type, @SuppressWarnings("unused") Object[] args, @SuppressWarnings("unused") PKeyword[] kwds,
                        @SuppressWarnings("unused") @Cached PyLongCheckNode longCheckNode) {
            throw raise(TypeError, ARGUMENT_MUST_BE_CALLABLE_OR_INTEGER_FUNCTION_ADDRESS);
        }

        @SuppressWarnings("unused")
        CThunkObject CThunkObject_new(int nArgs) {
            CThunkObject p = factory().createCThunkObject(PythonBuiltinClassType.CThunkObject, nArgs);

            p.pcl_write = null;
            p.pcl_exec = null;
            p.cif = null; // TODO init ffi_cif
            p.flags = 0;
            p.converters = null;
            p.callable = null;
            p.restype = null;
            p.setfunc = null;
            p.ffi_restype = null;

            for (int i = 0; i < nArgs + 1; ++i) {
                p.atypes[i] = null;
            }
            return p;
        }

        @SuppressWarnings("unused")
        CThunkObject _ctypes_alloc_callback(Object callable, Object[] converters, Object restype, int flags) {
            throw raise(NotImplementedError, toTruffleStringUncached("callbacks aren't supported yet."));
            /*- TODO
            int nArgs = converters.length;
            CThunkObject p = CThunkObject_new(nArgs);
            
            p.pcl_write = ffi_closure_alloc(sizeof(ffi_closure), p.pcl_exec);
            
            p.flags = flags;
            int i;
            for (i = 0; i < nArgs; ++i) {
                Object cnv = converters[i];
                p.atypes[i] = _ctypes_get_ffi_type(cnv, pyTypeStgDictNode);
            }
            p.atypes[i] = null;
            
            p.restype = restype;
            if (restype == PNone.NONE) {
                p.setfunc = FieldSet.nil;
                p.ffi_restype = ffi_type_void;
            } else {
                StgDictObject dict = pyTypeStgDictNode.execute(restype);
                if (dict == null || dict.setfunc == FieldSet.nil) {
                    throw raise(TypeError, INVALID_RESULT_TYPE_FOR_CALLBACK_FUNCTION);
                }
                p.setfunc = dict.setfunc;
                p.ffi_restype = dict.ffi_type_pointer;
            }
            
            ffi_abi cc = FFI_DEFAULT_ABI;
            int result = ffi_prep_cif(p.cif, cc, nArgs, _ctypes_get_ffi_type(restype, pyTypeStgDictNode), p.atypes);
            if (result != FFI_OK) {
                throw raise(RuntimeError, FFI_PREP_CIF_FAILED_WITH_D, result);
            }
            result = ffi_prep_closure_loc(p.pcl_write, p.cif, closure_fcn, p, p.pcl_exec);
            if (result != FFI_OK) {
                throw raise(RuntimeError, FFI_PREP_CLOSURE_FAILED_WITH_D, result);
            }
            
            p.converters = converters;
            p.callable = callable;
            return p;
            */
        }

    }

    @Builtin(name = "errcheck", minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2, isGetter = true, isSetter = true, doc = "a function to check for errors")
    @GenerateNodeFactory
    protected abstract static class PointerErrCheckNode extends PythonBinaryBuiltinNode {

        @Specialization(guards = "isNoValue(value)")
        Object PyCFuncPtr_get_errcheck(PyCFuncPtrObject self, @SuppressWarnings("unused") PNone value) {
            if (self.errcheck != null) {
                return self.errcheck;
            }
            return PNone.NONE;
        }

        @Specialization(guards = "!isNoValue(value)")
        Object PyCFuncPtr_set_errcheck(PyCFuncPtrObject self, Object value,
                        @Cached PyCallableCheckNode callableCheck) {
            if (value != PNone.NONE && !callableCheck.execute(value)) {
                throw raise(TypeError, THE_ERRCHECK_ATTRIBUTE_MUST_BE_CALLABLE);
            }
            self.errcheck = value;
            return PNone.NONE;
        }
    }

    @ImportStatic(PyCFuncPtrTypeBuiltins.class)
    @Builtin(name = "restype", minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2, isGetter = true, isSetter = true, doc = "specify the result type")
    @GenerateNodeFactory
    protected abstract static class PointerResTypeNode extends PythonBinaryBuiltinNode {

        @Specialization(guards = "isNoValue(value)")
        Object PyCFuncPtr_get_restype(PyCFuncPtrObject self, @SuppressWarnings("unused") PNone value,
                        @Cached PyObjectStgDictNode pyObjectStgDictNode) {
            if (self.restype != null) {
                return self.restype;
            }
            StgDictObject dict = pyObjectStgDictNode.execute(self);
            assert dict != null : "Cannot be NULL for PyCFuncPtrObject instances";
            if (dict.restype != null) {
                return dict.restype;
            } else {
                return PNone.NONE;
            }
        }

        @Specialization(guards = "!isNoValue(value)")
        Object PyCFuncPtr_set_restype(PyCFuncPtrObject self, Object value,
                        @Cached("create(T__check_retval_)") LookupAttributeInMRONode lookupAttr,
                        @Cached PyTypeStgDictNode pyTypeStgDictNode,
                        @Cached PyCallableCheckNode callableCheck) {
            if (value == PNone.NONE) {
                self.checker = null;
                self.restype = null;
                return PNone.NONE;
            }
            if (pyTypeStgDictNode.execute(value) != null && !callableCheck.execute(value)) {
                throw raise(TypeError, RESTYPE_MUST_BE_A_TYPE_A_CALLABLE_OR_NONE);
            }
            if (!PGuards.isPFunction(value)) {
                self.checker = lookupAttr.execute(value);
            }
            self.restype = value;
            return PNone.NONE;
        }
    }

    @Builtin(name = "argtypes", minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2, isGetter = true, isSetter = true, doc = "specify the argument types")
    @GenerateNodeFactory
    protected abstract static class PointerArgTypesNode extends PythonBinaryBuiltinNode {

        @Specialization(guards = {"isNoValue(value)", "self.argtypes != null"})
        Object PyCFuncPtr_get_argtypes(PyCFuncPtrObject self, @SuppressWarnings("unused") PNone value) {
            return factory().createTuple(self.argtypes);
        }

        @Specialization(guards = {"isNoValue(value)", "self.argtypes == null"})
        Object PyCFuncPtr_get_argtypes(PyCFuncPtrObject self, @SuppressWarnings("unused") PNone value,
                        @Cached PyTypeStgDictNode pyTypeStgDictNode) {
            StgDictObject dict = pyTypeStgDictNode.execute(self);
            assert dict != null : "Cannot be NULL for PyCFuncPtrObject instances";
            if (dict.argtypes != null) {
                return factory().createTuple(dict.argtypes);
            } else {
                return PNone.NONE;
            }
        }

        @Specialization(guards = "!isNoValue(value)")
        Object PyCFuncPtr_set_argtypes(PyCFuncPtrObject self, PNone value) {
            self.converters = null;
            self.argtypes = null;
            return value;
        }

        @Specialization(guards = "!isNoValue(value)")
        Object PyCFuncPtr_set_argtypes(PyCFuncPtrObject self, PTuple value,
                        @Cached LookupAttributeInMRONode.Dynamic lookupAttr,
                        @Cached GetInternalObjectArrayNode getArray) {
            Object[] ob = getArray.execute(value.getSequenceStorage());
            self.converters = converters_from_argtypes(ob, getRaiseNode(), lookupAttr);
            self.argtypes = ob;
            return PNone.NONE;
        }

        @Specialization(guards = "!isNoValue(value)")
        Object PyCFuncPtr_set_argtypes(PyCFuncPtrObject self, PList value,
                        @Cached LookupAttributeInMRONode.Dynamic lookupAttr,
                        @Cached GetInternalObjectArrayNode getArray) {
            Object[] ob = getArray.execute(value.getSequenceStorage());
            self.converters = converters_from_argtypes(ob, getRaiseNode(), lookupAttr);
            self.argtypes = ob;
            return PNone.NONE;
        }

        @Fallback
        Object error(@SuppressWarnings("unused") Object self, @SuppressWarnings("unused") Object value) {
            throw raise(TypeError, ARGTYPES_MUST_BE_A_SEQUENCE_OF_TYPES);
        }

    }

    @Builtin(name = J___REPR__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class ReprNode extends PythonUnaryBuiltinNode {

        @Specialization
        TruffleString PyCFuncPtr_repr(CDataObject self,
                        @Cached GetClassNode getClassNode,
                        @Cached GetNameNode getNameNode,
                        @Cached SimpleTruffleStringFormatNode simpleTruffleStringFormatNode) {
            Object clazz = getClassNode.execute(self);
            return simpleTruffleStringFormatNode.format("<%s object at %s>",
                            getNameNode.execute(clazz), getNameNode.execute(getClassNode.execute(self)));
        }
    }

    @Builtin(name = J___CALL__, minNumOfPositionalArgs = 1, takesVarArgs = true, takesVarKeywordArgs = true)
    @GenerateNodeFactory
    protected abstract static class PyCFuncPtrCallNode extends PythonVarargsBuiltinNode {

        @Specialization
        Object PyCFuncPtr_call(VirtualFrame frame, PyCFuncPtrObject self, Object[] inargs, PKeyword[] kwds,
                        @Cached PyTypeCheck pyTypeCheck,
                        @Cached PyLongAsVoidPtr asVoidPtr,
                        @Cached CallNode callNode,
                        @Cached PyObjectStgDictNode pyObjectStgDictNode,
                        @Cached GetInternalObjectArrayNode getArray,
                        @Cached CastToJavaIntExactNode castToJavaIntExactNode,
                        @Cached CastToTruffleStringNode castToTruffleStringNode,
                        @Cached PyTypeStgDictNode pyTypeStgDictNode,
                        @Cached GetNameNode getNameNode,
                        @Cached LookupAndCallUnaryDynamicNode lookupAndCallUnaryDynamicNode,
                        @Cached CallProcNode callProcNode,
                        @Cached TruffleString.EqualNode equalNode) {
            StgDictObject dict = pyObjectStgDictNode.execute(self);
            assert dict != null : "Cannot be NULL for PyCFuncPtrObject instances";
            Object restype = self.restype != null ? self.restype : dict.restype;
            Object[] converters = self.converters != null ? self.converters : dict.converters;
            Object checker = self.checker != null ? self.checker : dict.checker;
            Object[] argtypes = self.argtypes != null ? self.argtypes : dict.argtypes;
            /* later, we probably want to have an errcheck field in stgdict */
            Object errcheck = self.errcheck /* ? self.errcheck : dict.errcheck */;

            int[] props = new int[3];
            NativeFunction pProc = getFunctionFromLongObject(self.b_ptr.getNativePointer(), getContext(), asVoidPtr);
            Object[] callargs = _build_callargs(frame, self, argtypes, inargs, kwds, props,
                            pyTypeCheck, getArray, castToJavaIntExactNode, castToTruffleStringNode, pyTypeStgDictNode, callNode, getNameNode, equalNode);
            int inoutmask = props[pinoutmask_idx];
            int outmask = props[poutmask_idx];
            int numretvals = props[pnumretvals_idx];

            if (converters != null) {
                int required = converters.length;
                int actual = callargs.length;

                if ((dict.flags & FUNCFLAG_CDECL) == FUNCFLAG_CDECL) {
                    /*
                     * For cdecl functions, we allow more actual arguments than the length of the
                     * argtypes tuple.
                     */
                    if (required > actual) {
                        throw raise(TypeError, THIS_FUNCTION_TAKES_AT_LEAST_D_ARGUMENT_S_D_GIVEN,
                                        required, required == 1 ? "" : "s", actual);
                    }
                } else if (required != actual) {
                    throw raise(TypeError, THIS_FUNCTION_TAKES_D_ARGUMENT_S_D_GIVEN,
                                    required, required == 1 ? "" : "s", actual);
                }
            }
            CtypesThreadState state = CtypesThreadState.get(getContext(), PythonLanguage.get(this));
            CtypesModuleBuiltins ctypesModuleBuiltins = (CtypesModuleBuiltins) getContext().lookupBuiltinModule(T__CTYPES).getBuiltins();
            Object result = callProcNode.execute(frame, pProc,
                            callargs,
                            dict.flags,
                            argtypes,
                            converters,
                            restype,
                            checker,
                            state,
                            factory(),
                            getContext(),
                            ctypesModuleBuiltins);
            /* The 'errcheck' protocol */
            if (result != null && errcheck != null) {
                Object v = callNode.execute(frame, errcheck, result, self, callargs);
                /*
                 * If the errcheck function returned callargs unchanged, continue normal processing.
                 * If the errcheck function returned something else, use that as result.
                 */
                if (v != callargs) {
                    return v;
                }
            }

            return _build_result(result, callargs, outmask, inoutmask, numretvals,
                            lookupAndCallUnaryDynamicNode);
        }

        protected Object _get_arg(int[] pindex, TruffleString name, Object defval, Object[] inargs, PKeyword[] kwds, TruffleString.EqualNode equalNode) {
            if (pindex[0] < inargs.length) {
                return inargs[pindex[0]++];
            }
            if (kwds != null && name != null) {
                int v = KeywordsStorage.findStringKey(kwds, name, equalNode);
                if (v != -1) {
                    ++pindex[0];
                    return kwds[v].getValue();
                }
            }
            if (defval != null) {
                return defval;
            }
            /* we can't currently emit a better error message */
            if (name != null) {
                throw raise(TypeError, REQUIRED_ARGUMENT_S_MISSING, name);
            } else {
                throw raise(TypeError, NOT_ENOUGH_ARGUMENTS);
            }
        }

        private static final int poutmask_idx = 0;
        private static final int pinoutmask_idx = 0;
        private static final int pnumretvals_idx = 0;

        /*
         * This function implements higher level functionality plus the ability to call functions
         * with keyword arguments by looking at parameter flags. parameter flags is a tuple of 1, 2
         * or 3-tuples. The first entry in each is an integer specifying the direction of the data
         * transfer for this parameter - 'in', 'out' or 'inout' (zero means the same as 'in'). The
         * second entry is the parameter name, and the third is the default value if the parameter
         * is missing in the function call.
         *
         * This function builds and returns a new tuple 'callargs' which contains the parameters to
         * use in the call. Items on this tuple are copied from the 'inargs' tuple for 'in' and 'in,
         * out' parameters, and constructed from the 'argtypes' tuple for 'out' parameters. It also
         * calculates numretvals which is the number of return values for the function,
         * outmask/inoutmask are bitmasks containing indexes into the callargs tuple specifying
         * which parameters have to be returned. _build_result builds the return value of the
         * function.
         */
        @SuppressWarnings("fallthrough")
        Object[] _build_callargs(VirtualFrame frame, PyCFuncPtrObject self, Object[] argtypes, Object[] inargs, PKeyword[] kwds, int[] props,
                        PyTypeCheck pyTypeCheck,
                        GetInternalObjectArrayNode getArray,
                        CastToJavaIntExactNode castToJavaIntExactNode,
                        CastToTruffleStringNode castToTruffleStringNode,
                        PyTypeStgDictNode pyTypeStgDictNode,
                        CallNode callNode,
                        GetNameNode getNameNode,
                        TruffleString.EqualNode equalNode) {
            /*
             * It's a little bit difficult to determine how many arguments the function call
             * requires/accepts. For simplicity, we count the consumed args and compare this to the
             * number of supplied args.
             */

            props[poutmask_idx] = 0;

            /* Trivial cases, where we either return inargs itself, or a slice of it. */
            if (argtypes == null || self.paramflags == null || argtypes.length == 0) {
                return inargs;
            }

            Object[] paramflags = self.paramflags;
            int len = argtypes.length;
            Object[] callargs = new Object[len]; /* the argument tuple we build */
            int[] inargs_index = new int[1];
            for (int i = 0; i < len; ++i) {
                /*
                 * This way seems to be ~2 us faster than the PyArg_ParseTuple calls below.
                 */
                /* We HAVE already checked that the tuple can be parsed with "i|ZO", so... */
                Object[] item = getArray.execute(((PTuple) paramflags[i]).getSequenceStorage());
                Object ob;
                int tsize = item.length;
                int flag = castToJavaIntExactNode.execute(item[0]);
                TruffleString name = tsize > 1 ? castToTruffleStringNode.execute(item[1]) : null;
                Object defval = tsize > 2 ? item[2] : null;

                switch (flag & (PARAMFLAG_FIN | PARAMFLAG_FOUT | PARAMFLAG_FLCID)) {
                    case PARAMFLAG_FIN | PARAMFLAG_FLCID:
                        /*
                         * ['in', 'lcid'] parameter. Always taken from defval, if given, else the
                         * integer 0.
                         */
                        if (defval == null) {
                            defval = 0;
                        }
                        callargs[i] = defval;
                        break;
                    case (PARAMFLAG_FIN | PARAMFLAG_FOUT):
                        props[pinoutmask_idx] |= (1 << i); /* mark as inout arg */
                        (props[pnumretvals_idx])++;
                        /* fall through */
                    case 0:
                    case PARAMFLAG_FIN:
                        /* 'in' parameter. Copy it from inargs. */
                        ob = _get_arg(inargs_index, name, defval, inargs, kwds, equalNode);
                        callargs[i] = ob;
                        break;
                    case PARAMFLAG_FOUT:
                        /* XXX Refactor this code into a separate function. */
                        /*
                         * 'out' parameter. argtypes[i] must be a POINTER to a c type. Cannot by
                         * supplied in inargs, but a defval will be used if available. XXX Should we
                         * support getting it from kwds?
                         */
                        if (defval != null) {
                            /*
                             * XXX Using mutable objects as defval will make the function
                             * non-threadsafe, unless we copy the object in each invocation
                             */
                            callargs[i] = defval;
                            props[poutmask_idx] |= (1 << i); /* mark as out arg */
                            props[pnumretvals_idx]++;
                            break;
                        }
                        ob = argtypes[i];
                        StgDictObject dict = pyTypeStgDictNode.execute(ob);
                        if (dict == null) {
                            /*
                             * Cannot happen: _validate_paramflags() would not accept such an object
                             */
                            throw raise(RuntimeError, NULL_STGDICT_UNEXPECTED);
                        }
                        if (PGuards.isString(dict.proto)) { // TODO Py_TPFLAGS_UNICODE_SUBCLASS
                            throw raise(TypeError, S_OUT_PARAMETER_MUST_BE_PASSED_AS_DEFAULT_VALUE, getNameNode.execute(ob));
                        }
                        if (pyTypeCheck.isPyCArrayTypeObject(ob)) {
                            ob = callNode.execute(frame, ob);
                        } else {
                            /* Create an instance of the pointed-to type */
                            ob = callNode.execute(frame, dict.proto);
                        }
                        /*
                         * The .from_param call that will occur later will pass this as a byref
                         * parameter.
                         */
                        callargs[i] = ob;
                        props[poutmask_idx] |= (1 << i); /* mark as out arg */
                        props[pnumretvals_idx]++;
                        break;
                    default:
                        throw raise(ValueError, PARAMFLAG_D_NOT_YET_IMPLEMENTED, flag);
                }
            }

            /*
             * We have counted the arguments we have consumed in 'inargs_index'. This must be the
             * same as len(inargs) + len(kwds), otherwise we have either too much or not enough
             * arguments.
             */

            int actual_args = inargs.length + (kwds != null ? kwds.length : 0);
            if (actual_args != inargs_index[0]) {
                /*
                 * When we have default values or named parameters, this error message is
                 * misleading. See unittests/test_paramflags.py
                 */
                throw raise(TypeError, CALL_TAKES_EXACTLY_D_ARGUMENTS_D_GIVEN, inargs_index, actual_args);
            }

            /*
             * outmask is a bitmask containing indexes into callargs. Items at these indexes contain
             * values to return.
             */
            return callargs;
        }

        /*
         * Build return value of a function. Consumes the refcount on result and callargs.
         */
        Object _build_result(Object result, Object[] callargs, int outmask, int inoutmask, int numretvals,
                        LookupAndCallUnaryDynamicNode lookupAndCallUnaryDynamicNode) {
            int i, index;
            int bit;
            Object[] tup = null;

            if (callargs == null) {
                return result;
            }
            if (result == null || numretvals == 0) {
                return result;
            }
            /* tup will not be allocated if numretvals == 1 */
            /* allocate tuple to hold the result */
            if (numretvals > 1) {
                tup = new Object[numretvals];
            }

            index = 0;
            for (bit = 1, i = 0; i < 32; ++i, bit <<= 1) {
                Object v;
                if ((bit & inoutmask) != 0) {
                    v = callargs[i];
                    if (numretvals == 1) {
                        return v;
                    }
                    assert tup != null;
                    tup[index] = v;
                    index++;
                } else if ((bit & outmask) != 0) {
                    v = callargs[i];
                    v = lookupAndCallUnaryDynamicNode.executeObject(v, T___ctypes_from_outparam__);
                    if (v == null || numretvals == 1) {
                        return v;
                    }
                    assert tup != null;
                    tup[index] = v;
                    index++;
                }
                if (index == numretvals) {
                    break;
                }
            }

            return tup;
        }
    }

    protected abstract static class PyCFuncPtrFromDllNode extends PNodeWithRaise {

        private static final char[] PzZ = "PzZ".toCharArray();

        abstract Object execute(VirtualFrame frame, Object type, Object[] args, PythonContext context, PythonObjectFactory factory);

        @Specialization
        Object PyCFuncPtr_FromDll(VirtualFrame frame, Object type, Object[] args, PythonContext context, PythonObjectFactory factory,
                        @Cached PyTypeCheck pyTypeCheck,
                        @Cached CtypesDlSymNode dlSymNode,
                        @Cached PyLongAsVoidPtr asVoidPtr,
                        @Cached PyLongCheckNode longCheckNode,
                        @Cached GetInternalObjectArrayNode getArray,
                        @Cached CastToTruffleStringNode toString,
                        @Cached KeepRefNode keepRefNode,
                        @Cached GetAnyAttributeNode getAttributeNode,
                        @Cached PyTypeStgDictNode pyTypeStgDictNode,
                        @Cached AuditNode auditNode,
                        @Cached TruffleString.CodePointAtIndexNode codePointAtIndexNode) {
            // PyArg_ParseTuple(args, "O|O", &tuple, &paramflags);
            PTuple tuple = (PTuple) args[0];
            Object[] paramflags = null;
            if (args.length > 1 && args[1] instanceof PTuple) {
                paramflags = getArray.execute(((PTuple) args[1]).getSequenceStorage());
            }

            // PyArg_ParseTuple(ftuple, "O&O;illegal func_spec argument", _get_name, &name, &dll)
            Object[] ftuple = getArray.execute(tuple.getSequenceStorage());
            TruffleString name = toString.execute(ftuple[0]);
            Object dll = ftuple[1];
            auditNode.audit("ctypes.dlsym", dll, name);

            Object obj = getAttributeNode.executeObject(frame, dll, T__HANDLE);
            if (!(obj instanceof PythonNativeVoidPtr) && !longCheckNode.execute(obj)) { // PyLong_Check
                throw raise(TypeError, THE_HANDLE_ATTRIBUTE_OF_THE_SECOND_ARGUMENT_MUST_BE_AN_INTEGER);
            }
            DLHandler handle = getHandleFromLongObject(obj, context, asVoidPtr, getRaiseNode());
            Object address = dlSymNode.execute(frame, handle, name, context, factory, AttributeError);
            _validate_paramflags(type, paramflags, pyTypeCheck, getArray, pyTypeStgDictNode, codePointAtIndexNode);

            PyCFuncPtrObject self = factory.createPyCFuncPtrObject(type);
            StgDictObject dict = pyTypeStgDictNode.checkAbstractClass(type, getRaiseNode());
            GenericPyCDataNew(dict, self);
            self.paramflags = paramflags;

            self.b_ptr = PtrValue.nativePointer(address); // TODO
            keepRefNode.execute(frame, self, 0, dll, factory);

            self.callable = self;
            return self;
        }

        /* Returns 1 on success, 0 on error */
        void _validate_paramflags(Object type, Object[] paramflags,
                        PyTypeCheck pyTypeCheck,
                        GetInternalObjectArrayNode getArray,
                        PyTypeStgDictNode pyTypeStgDictNode,
                        TruffleString.CodePointAtIndexNode codePointAtIndexNode) {
            StgDictObject dict = pyTypeStgDictNode.checkAbstractClass(type, getRaiseNode());
            Object[] argtypes = dict.argtypes;

            if (paramflags == null || dict.argtypes == null) {
                return;
            }

            if (!PGuards.isPTuple(paramflags)) {
                throw raise(TypeError, PARAMFLAGS_MUST_BE_A_TUPLE_OR_NONE);
            }

            int len = paramflags.length;
            if (len != dict.argtypes.length) {
                throw raise(ValueError, PARAMFLAGS_MUST_HAVE_THE_SAME_LENGTH_AS_ARGTYPES);
            }

            for (int i = 0; i < len; ++i) {
                PTuple item = (PTuple) paramflags[i];
                // PyArg_ParseTuple(item, "i|ZO", &flag, &name, &defval)
                Object[] array = getArray.execute(item.getSequenceStorage());
                int flag = (int) array[0];
                if (array.length > 1) {
                    if (!PGuards.isString(array[1])) {
                        throw raise(TypeError, PARAMFLAGS_MUST_BE_A_SEQUENCE_OF_INT_STRING_VALUE_TUPLES);
                    }
                }
                Object typ = argtypes[i];
                switch (flag & (PARAMFLAG_FIN | PARAMFLAG_FOUT | PARAMFLAG_FLCID)) {
                    case 0:
                    case PARAMFLAG_FIN:
                    case PARAMFLAG_FIN | PARAMFLAG_FLCID:
                    case PARAMFLAG_FIN | PARAMFLAG_FOUT:
                        break;
                    case PARAMFLAG_FOUT:
                        _check_outarg_type(typ, i + 1, pyTypeCheck, pyTypeStgDictNode, codePointAtIndexNode);
                        break;
                    default:
                        throw raise(TypeError, PARAMFLAG_VALUE_D_NOT_SUPPORTED, flag);
                }
            }
        }

        /* Return 1 if usable, 0 else and exception set. */
        void _check_outarg_type(Object arg, int index,
                        PyTypeCheck pyTypeCheck,
                        PyTypeStgDictNode pyTypeStgDictNode,
                        TruffleString.CodePointAtIndexNode codePointAtIndexNode) {
            if (pyTypeCheck.isPyCPointerTypeObject(arg)) {
                return;
            }

            if (pyTypeCheck.isPyCArrayTypeObject(arg)) {
                return;
            }

            StgDictObject dict = pyTypeStgDictNode.execute(arg);
            if (dict != null /* simple pointer types, c_void_p, c_wchar_p, BSTR, ... */
                            && PGuards.isTruffleString(dict.proto)
                            /*
                             * We only allow c_void_p, c_char_p and c_wchar_p as a simple output
                             * parameter type
                             */
                            && strchr(PzZ, codePointAtIndexNode.execute((TruffleString) dict.proto, 0, TS_ENCODING))) {
                return;
            }

            throw raise(TypeError, OUT_PARAMETER_D_MUST_BE_A_POINTER_TYPE_NOT_S, index, GetNameNode.getUncached().execute(arg));
        }

        protected static boolean strchr(char[] chars, int code) {
            for (char c : chars) {
                if (c == code) {
                    return true;
                }
            }
            return false;
        }
    }
}
