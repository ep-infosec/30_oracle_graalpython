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

PyTypeObject PySet_Type = PY_TRUFFLE_TYPE("set", &PyType_Type, Py_TPFLAGS_DEFAULT | Py_TPFLAGS_BASETYPE | Py_TPFLAGS_HAVE_GC | _Py_TPFLAGS_MATCH_SELF, sizeof(PySetObject));
PyTypeObject PyFrozenSet_Type = PY_TRUFFLE_TYPE("frozenset", &PyType_Type, Py_TPFLAGS_DEFAULT | Py_TPFLAGS_BASETYPE | Py_TPFLAGS_HAVE_GC | _Py_TPFLAGS_MATCH_SELF, sizeof(PySetObject));

UPCALL_ID(PySet_New);
PyObject * PySet_New(PyObject *iterable) {
    return UPCALL_CEXT_O(_jls_PySet_New, native_to_java(iterable != NULL ? iterable : Py_None));
}

UPCALL_ID(PyFrozenSet_New);
PyObject * PyFrozenSet_New(PyObject *iterable) {
    return UPCALL_CEXT_O(_jls_PyFrozenSet_New, native_to_java(iterable));
}

typedef Py_ssize_t (*set_size_fun_t)(PyObject *anyset);
UPCALL_TYPED_ID(PySet_Size, set_size_fun_t);
Py_ssize_t PySet_Size(PyObject *anyset) {
    return PySet_GET_SIZE(anyset);
}

UPCALL_ID(PySet_Add);
int PySet_Add(PyObject *set, PyObject *key) {
    return UPCALL_CEXT_I(_jls_PySet_Add, native_to_java(set), native_to_java(key));
}

UPCALL_ID(PySet_Contains);
int PySet_Contains(PyObject *anyset, PyObject *key) {
    return UPCALL_CEXT_I(_jls_PySet_Contains, native_to_java(anyset), native_to_java(key));
}

UPCALL_ID(PySet_NextEntry);
int _PySet_NextEntry(PyObject *set, Py_ssize_t *pos, PyObject **key, Py_hash_t *hash) {
    PyObject *tresult = UPCALL_CEXT_O(_jls_PySet_NextEntry, native_to_java(set), *pos);
    if (tresult == NULL) {
        *key = NULL;
        *hash = 0;
    	return 0;
    }
    (*pos)++;
    *key = PyTuple_GetItem(tresult, 0);
    *hash = PyLong_AsSsize_t(PyTuple_GetItem(tresult, 1));
    return 1;
}

UPCALL_ID(PySet_Pop);
PyObject * PySet_Pop(PyObject *set) {
    return UPCALL_CEXT_O(_jls_PySet_Pop, native_to_java(set));
}

UPCALL_ID(PySet_Discard);
int PySet_Discard(PyObject *set, PyObject *key) {
    return UPCALL_CEXT_I(_jls_PySet_Discard, native_to_java(set), native_to_java(key));
}

UPCALL_ID(PySet_Clear);
int PySet_Clear(PyObject *set) {
    return UPCALL_CEXT_I(_jls_PySet_Clear, native_to_java(set));
}
