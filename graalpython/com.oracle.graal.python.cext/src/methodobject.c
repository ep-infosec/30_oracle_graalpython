/*
 * Copyright (c) 2018, 2022, Oracle and/or its affiliates. All rights reserved.
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
#include "capi.h"

/* undefine macro trampoline to PyCFunction_NewEx */
#undef PyCFunction_New
/* undefine macro trampoline to PyCMethod_New */
#undef PyCFunction_NewEx

typedef PyObject *(*PyCFunction)(PyObject *, PyObject *);

PyObject *PyCFunction_New(PyMethodDef *ml, PyObject *self) {
    return PyCFunction_NewEx(ml, self, NULL);
}

PyObject *PyCFunction_NewEx(PyMethodDef *ml, PyObject *self, PyObject *module) {
    return PyCMethod_New(ml, self, module, NULL);
}

PyTypeObject PyCFunction_Type = PY_TRUFFLE_TYPE_WITH_VECTORCALL("builtin_function_or_method", &PyType_Type, Py_TPFLAGS_DEFAULT | Py_TPFLAGS_HAVE_GC | _Py_TPFLAGS_HAVE_VECTORCALL, sizeof(PyCFunctionObject), offsetof(PyCFunctionObject, vectorcall));
PyTypeObject PyCMethod_Type = PY_TRUFFLE_TYPE_WITH_VECTORCALL("builtin_method", &PyCFunction_Type, Py_TPFLAGS_DEFAULT | Py_TPFLAGS_HAVE_GC | _Py_TPFLAGS_HAVE_VECTORCALL, sizeof(PyCFunctionObject), offsetof(PyCFunctionObject, vectorcall));

typedef PyObject* (*PyCMethod_New_fun_t)(PyMethodDef* methodDef,
                                                    void* name,
                                                    void* methObj, 
                                                    int flags, 
                                                    int wrapper,
                                                    void* self,
                                                    void* module, 
                                                    void* cls,
                                                    const char* doc);
UPCALL_TYPED_ID(PyCMethod_New, PyCMethod_New_fun_t);
PyObject* PyCMethod_New(PyMethodDef *ml, PyObject *self, PyObject *module, PyTypeObject *cls) {
    return _jls_PyCMethod_New(ml,
                                               polyglot_from_string(ml->ml_name, SRC_CS),
                                               function_pointer_to_java(ml->ml_meth),
                                               ml->ml_flags,
                                               get_method_flags_wrapper(ml->ml_flags),
                                               native_to_java(self),
                                               native_to_java(module),
                                               native_to_java((PyObject*)cls),
                                               ml->ml_doc);
}
