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

PyObject * PyThreadState_GetDict() {
	return PyThreadState_Get()->dict;
}

PyThreadState *
_PyThreadState_UncheckedGet(void)
{
    return polyglot_invoke(PY_TRUFFLE_CEXT, "PyThreadState_Get");
}

PyThreadState * PyThreadState_Get() {
    return polyglot_invoke(PY_TRUFFLE_CEXT, "PyThreadState_Get");
}

int64_t
PyInterpreterState_GetID(PyInterpreterState *interp)
{
    return 0;
}

PyInterpreterState* PyInterpreterState_Main()
{
    // TODO: not yet supported
    return NULL;
}

typedef PyGILState_STATE (*py_gil_state_ensure_fun_t)();
UPCALL_TYPED_ID(PyGILState_Ensure, py_gil_state_ensure_fun_t);
PyGILState_STATE PyGILState_Ensure() {
    return _jls_PyGILState_Ensure();
}

typedef void (*py_gil_state_release_fun_t)(PyGILState_STATE);
UPCALL_TYPED_ID(PyGILState_Release, py_gil_state_release_fun_t);
void PyGILState_Release(PyGILState_STATE oldstate) {
    _jls_PyGILState_Release(oldstate);
}

PyThreadState* PyGILState_GetThisThreadState(void) {
    // TODO this should return NULL when called from a thread that is not known to python
    return polyglot_invoke(PY_TRUFFLE_CEXT, "PyThreadState_Get");
}

typedef PyObject* (*find_module_fun_t)(long index);
UPCALL_TYPED_ID(PyState_FindModule, find_module_fun_t);
PyObject* PyState_FindModule(struct PyModuleDef* module) {
    Py_ssize_t index = module->m_base.m_index;
    if (module->m_slots) {
        return NULL;
    } else if (index == 0) {
        return NULL;
    } else {
        return _jls_PyState_FindModule(index);
    }
}

int PyState_AddModule(PyObject* module, struct PyModuleDef* def) {
    Py_ssize_t index;
    if (!def) {
        Py_FatalError("PyState_AddModule: Module Definition is NULL");
        return -1;
    }
    // TODO(fa): check if module was already added

    if (def->m_slots) {
        PyErr_SetString(PyExc_SystemError,
                        "PyState_AddModule called on module with slots");
        return -1;
    }

    // TODO(fa): implement
    return 0;
}

int PyState_RemoveModule(struct PyModuleDef* def) {
    Py_ssize_t index = def->m_base.m_index;
    if (def->m_slots) {
        PyErr_SetString(PyExc_SystemError,
                        "PyState_RemoveModule called on module with slots");
        return -1;
    }
    if (index == 0) {
        Py_FatalError("PyState_RemoveModule: Module index invalid.");
        return -1;
    }
    // TODO(fa): implement
    return 0;
}
