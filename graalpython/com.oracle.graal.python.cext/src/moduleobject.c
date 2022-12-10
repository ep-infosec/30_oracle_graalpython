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

PyTypeObject PyModule_Type = PY_TRUFFLE_TYPE("module", &PyType_Type, Py_TPFLAGS_DEFAULT | Py_TPFLAGS_HAVE_GC | Py_TPFLAGS_BASETYPE, sizeof(PyModuleObject));
PyTypeObject PyModuleDef_Type = PY_TRUFFLE_TYPE("moduledef", &PyType_Type, 0, sizeof(struct PyModuleDef));


UPCALL_ID(_PyModule_GetAndIncMaxModuleNumber);
// partially taken from CPython "Objects/moduleobject.c"
PyObject*
PyModuleDef_Init(struct PyModuleDef* def)
{
    if (PyType_Ready(&PyModuleDef_Type) < 0)
         return NULL;
    if (def->m_base.m_index == 0) {
        Py_SET_REFCNT(def, 1);
        Py_SET_TYPE(def, &PyModuleDef_Type);
        def->m_base.m_index = UPCALL_CEXT_L(_jls__PyModule_GetAndIncMaxModuleNumber);
    }
    return (PyObject*)def;
}

//                                       method_def     module      name          cfunc   flags sig  doc
typedef int (*AddFunctionToModule_fun_t)(PyMethodDef *, PyObject *, const char *, void *, int , int, char *);
UPCALL_TYPED_ID(AddFunctionToModule, AddFunctionToModule_fun_t);
int PyModule_AddFunctions(PyObject* mod, PyMethodDef* methods) {
    if (!methods) {
        return -1;
    }
    for (PyMethodDef* def = methods; def->ml_name != NULL; def++) {
        _jls_AddFunctionToModule(def,
                       native_to_java(mod),
                       polyglot_from_string(def->ml_name, SRC_CS),
                       function_pointer_to_java(def->ml_meth),
                       def->ml_flags,
                       get_method_flags_wrapper(def->ml_flags),
					   def->ml_doc);
    }
    return 0;
}

UPCALL_ID(PyModule_SetDocString);
int PyModule_SetDocString(PyObject* m, const char* doc) {
    UPCALL_CEXT_VOID(_jls_PyModule_SetDocString, native_to_java(m), polyglot_from_string(doc, SRC_CS));
    return 0;
}

POLYGLOT_DECLARE_TYPE(PyModuleDef);
UPCALL_ID(_PyModule_CreateInitialized_PyModule_New);
PyObject* _PyModule_CreateInitialized(PyModuleDef* moduledef, int apiversion) {
	moduledef = native_pointer_to_java(moduledef);
    if (!PyModuleDef_Init(moduledef))
        return NULL;
    if (moduledef->m_slots) {
        PyErr_Format(
            PyExc_SystemError,
            "module %s: PyModule_Create is incompatible with m_slots", moduledef->m_name);
        return NULL;
    }

    PyModuleObject* mod = (PyModuleObject*)UPCALL_CEXT_O(_jls__PyModule_CreateInitialized_PyModule_New, polyglot_from_string(moduledef->m_name, SRC_CS));

    if (moduledef->m_size > 0) {
        void* md_state = PyMem_MALLOC(moduledef->m_size);
        if (!md_state) {
            PyErr_NoMemory();
            return NULL;
        }
        memset(md_state, 0, moduledef->m_size);
        mod->md_state = md_state;
    }

    if (moduledef->m_methods != NULL) {
        if (PyModule_AddFunctions((PyObject*) mod, moduledef->m_methods) != 0) {
            return NULL;
        }
    }

    if (moduledef->m_doc != NULL) {
        if (PyModule_SetDocString((PyObject*) mod, moduledef->m_doc) != 0) {
            return NULL;
        }
    }

    mod->md_def = polyglot_from_PyModuleDef(moduledef);
    return (PyObject*) mod;
}

PyObject* PyModule_Create2(PyModuleDef* moduledef, int apiversion) {
    return _PyModule_CreateInitialized(moduledef, apiversion);
}

PyObject* PyModule_GetDict(PyObject* o) {
	o = native_pointer_to_java(o);
    if (!PyModule_Check(o)) {
        PyErr_BadInternalCall();
        return NULL;
    }
    return ((PyModuleObject*)o)->md_dict;
}

UPCALL_ID(PyModule_NewObject);
PyObject* PyModule_NewObject(PyObject* name) {
    return UPCALL_CEXT_O(_jls_PyModule_NewObject, native_to_java(name));
}

PyObject* PyModule_New(const char *name) {
    return UPCALL_CEXT_O(_jls_PyModule_NewObject, polyglot_from_string(name, SRC_CS));
}

PyModuleDef* PyModule_GetDef(PyObject* m) {
	m = native_pointer_to_java(m);
    if (!PyModule_Check(m)) {
        PyErr_BadArgument();
        return NULL;
    }
    return ((PyModuleObject *)m)->md_def;
}

void* PyModule_GetState(PyObject *m) {
	m = native_pointer_to_java(m);
    if (!PyModule_Check(m)) {
        PyErr_BadArgument();
        return NULL;
    }
    return ((PyModuleObject *)m)->md_state;
}

UPCALL_ID(PyModule_GetNameObject);
PyObject* PyModule_GetNameObject(PyObject *m) {
	return UPCALL_CEXT_O(_jls_PyModule_GetNameObject, native_to_java(m));
}

// partially taken from CPython "Objects/moduleobject.c"
const char * PyModule_GetName(PyObject *m) {
    PyObject *name = PyModule_GetNameObject(m);
    if (name == NULL) {
        return NULL;
    }
    return PyUnicode_AsUTF8(name);
}
