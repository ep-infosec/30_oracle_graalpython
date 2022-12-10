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

#ifndef Py_DEFAULT_RECURSION_LIMIT
#define Py_DEFAULT_RECURSION_LIMIT 1000
#endif
int _Py_CheckRecursionLimit = Py_DEFAULT_RECURSION_LIMIT;


PyObject* PyEval_CallObjectWithKeywords(PyObject *func, PyObject *args, PyObject *kwargs) {
    return PyObject_Call(func, args, kwargs);
}

void PyEval_InitThreads() {
    // Nothing to do
}

int PyEval_ThreadsInitialized() {
    return 1;
}

typedef PyThreadState* (*save_thread_fun_t)();
UPCALL_TYPED_ID(PyEval_SaveThread, save_thread_fun_t);
PyThreadState* PyEval_SaveThread() {
    return _jls_PyEval_SaveThread();
}

typedef void (*restore_thread_fun_t)();
UPCALL_TYPED_ID(PyEval_RestoreThread, restore_thread_fun_t);
void PyEval_RestoreThread(PyThreadState *ptr) {
    return _jls_PyEval_RestoreThread();
}

UPCALL_ID(PyEval_GetBuiltins);
PyObject* PyEval_GetBuiltins() {
	return UPCALL_CEXT_O(_jls_PyEval_GetBuiltins);
}

int PyEval_MergeCompilerFlags(PyCompilerFlags *cf) {
    return 0;
}

UPCALL_ID(PyThread_allocate_lock);
void* PyThread_allocate_lock() {
    return UPCALL_CEXT_O(_jls_PyThread_allocate_lock);
}

UPCALL_ID(PyThread_acquire_lock);
int PyThread_acquire_lock(PyThread_type_lock aLock, int waitflag) {
    return UPCALL_CEXT_I(_jls_PyThread_acquire_lock, native_to_java(aLock), waitflag ? -1 : 0);
}

UPCALL_ID(PyThread_release_lock);
void PyThread_release_lock(PyThread_type_lock aLock) {
    UPCALL_CEXT_O(_jls_PyThread_release_lock, native_to_java(aLock));
}


void PyThread_free_lock(PyThread_type_lock lock) {
}

PyObject *
PyEval_EvalCode(PyObject *co, PyObject *globals, PyObject *locals)
{
    return PyEval_EvalCodeEx(co,
                      globals, locals,
                      (PyObject **)NULL, 0,
                      (PyObject **)NULL, 0,
                      (PyObject **)NULL, 0,
                      NULL, NULL);
}

typedef PyObject *(*eval_code_ex_fun_t)(PyObject *_co, PyObject *globals, PyObject *locals,
                  PyObject *const *args,
                  PyObject *const *kws,
                  PyObject *const *defs,
                  PyObject *kwdefs, PyObject *closure);
UPCALL_TYPED_ID(PyEval_EvalCodeEx, eval_code_ex_fun_t);
PyObject *
PyEval_EvalCodeEx(PyObject *_co, PyObject *globals, PyObject *locals,
                  PyObject *const *args, int argcount,
                  PyObject *const *kws, int kwcount,
                  PyObject *const *defs, int defcount,
                  PyObject *kwdefs, PyObject *closure)
{
    if (globals == NULL) {
        PyErr_SetString(PyExc_SystemError, "PyEval_EvalCodeEx: NULL globals");
        return NULL;
    }
    return _jls_PyEval_EvalCodeEx(native_to_java(_co), native_to_java(globals), native_to_java(locals != NULL ? locals : Py_None),
                                  polyglot_from_PyObjectPtr_array(args, argcount),
                                  polyglot_from_PyObjectPtr_array(kws, kwcount * 2),
                                  polyglot_from_PyObjectPtr_array(defs, defcount),
                                  native_to_java(kwdefs), native_to_java(closure));
}

#undef Py_EnterRecursiveCall
int Py_EnterRecursiveCall(const char *where) {
    return 0;
}

#undef Py_LeaveRecursiveCall
void Py_LeaveRecursiveCall(void) {}
