/*
 * Copyright (c) 2017, 2022, Oracle and/or its affiliates. All rights reserved.
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
#ifndef CAPI_H
#define CAPI_H

#define MUST_INLINE __attribute__((always_inline)) inline
#define NO_INLINE __attribute__((noinline))

#include <graalvm/llvm/polyglot.h>

#include "Python.h"
#include <truffle.h>
#include <graalvm/llvm/handles.h>
#include "datetime.h"
#include "structmember.h"
#include "frameobject.h"
#include "pycore_moduleobject.h"

#define SRC_CS "utf-8"

/* Flags definitions representing global (debug) options. */
#define PY_TRUFFLE_TRACE_MEM 0x1


/* Private types are defined here because we need to declare the type cast. */

typedef struct {
    PyObject_VAR_HEAD
    int readonly;
    void *buf_delegate;
} PyBufferDecorator;

/* Taken from CPython "Objects/descrobject.c".
 * This struct is actually private to 'descrobject.c' but we need to register
 * it to the managed property type. */
typedef struct {
    PyObject_HEAD
    PyObject *prop_get;
    PyObject *prop_set;
    PyObject *prop_del;
    PyObject *prop_doc;
    int getter_doc;
} propertyobject;

PyAPI_DATA(PyTypeObject) PyBuffer_Type;
PyAPI_DATA(PyTypeObject) _PyExc_BaseException;
PyAPI_DATA(PyTypeObject) _PyExc_StopIteration;

typedef void (*init_upcall)();

extern void *PY_TRUFFLE_CEXT;
extern void *PY_BUILTIN;
extern void *Py_NoValue;
extern init_upcall upcalls[];
extern unsigned init_upcall_n;
extern uint32_t Py_Truffle_Options;

/* upcall helpers */
MUST_INLINE
PyObject* polyglot_ensure_ptr(void *obj) {
	return polyglot_fits_in_i64(obj) ? (PyObject*) polyglot_as_i64(obj) : (PyObject*) obj;
}

MUST_INLINE
int32_t polyglot_ensure_i32(void *obj) {
	return polyglot_fits_in_i32(obj) ? polyglot_as_i32(obj) : (int32_t) obj;
}

MUST_INLINE
int64_t polyglot_ensure_i64(void *obj) {
	return polyglot_fits_in_i64(obj) ? polyglot_as_i64(obj) : (int64_t) obj;
}

MUST_INLINE
double polyglot_ensure_double(void *obj) {
	return polyglot_fits_in_double(obj) ? polyglot_as_double(obj) : (double) ((int64_t)obj);
}

MUST_INLINE
int PyTruffle_Trace_Memory() {
	return Py_Truffle_Options & PY_TRUFFLE_TRACE_MEM;
}

/* upcall functions for calling into Python */
extern void*(*pytruffle_decorate_function)(void *fun0, void* fun1);
extern PyObject*(*PY_TRUFFLE_LANDING_BORROWED)(void *rcv, void* name, ...);
extern PyObject*(*PY_TRUFFLE_LANDING_NEWREF)(void *rcv, void* name, ...);
extern void*(*PY_TRUFFLE_LANDING_L)(void *rcv, void* name, ...);
extern void*(*PY_TRUFFLE_LANDING_D)(void *rcv, void* name, ...);
extern void*(*PY_TRUFFLE_LANDING_PTR)(void *rcv, void* name, ...);
extern PyObject*(*PY_TRUFFLE_CEXT_LANDING_BORROWED)(void* name, ...);
extern PyObject*(*PY_TRUFFLE_CEXT_LANDING_NEWREF)(void* name, ...);
extern void* (*PY_TRUFFLE_CEXT_LANDING_L)(void* name, ...);
extern void* (*PY_TRUFFLE_CEXT_LANDING_D)(void* name, ...);
extern void* (*PY_TRUFFLE_CEXT_LANDING_PTR)(void* name, ...);

/* Call function with return type 'PyObject *'; does polyglot cast and error handling */
#define UPCALL_O(__recv__, __name__, ...) PY_TRUFFLE_LANDING_NEWREF((__recv__), __name__, ##__VA_ARGS__)

/* Call function with return type 'PyObject *'; does polyglot cast and error handling */
#define UPCALL_BORROWED(__recv__, __name__, ...) PY_TRUFFLE_LANDING_BORROWED((__recv__), __name__, ##__VA_ARGS__)

/* Call function with a primitive return; no polyglot cast but error handling */
#define UPCALL_P(__recv__, __name__, ...) (PY_TRUFFLE_LANDING_L((__recv__), __name__, ##__VA_ARGS__))

/* Call function with return type 'int'; no polyglot cast but error handling */
#define UPCALL_I(__recv__, __name__, ...) (polyglot_ensure_i32(UPCALL_P(__recv__, __name__, ##__VA_ARGS__)))

/* Call function with return type 'long'; no polyglot cast but error handling */
#define UPCALL_L(__recv__, __name__, ...) (polyglot_ensure_i64(UPCALL_P(__recv__, __name__, ##__VA_ARGS__)))

/* Call function with return type 'double'; no polyglot cast but error handling */
#define UPCALL_D(__recv__, __name__, ...) (polyglot_ensure_double(PY_TRUFFLE_LANDING_D((__recv__), __name__, ##__VA_ARGS__)))

/* Call function with return type 'void*'; no polyglot cast and no error handling */
#define UPCALL_PTR(__recv__, __name__, ...) (polyglot_ensure_ptr(PY_TRUFFLE_LANDING_PTR((__recv__), __name__, ##__VA_ARGS__)))

/* Call function of 'python_cext' module with return type 'PyObject *'; does polyglot cast and error handling */
#define UPCALL_CEXT_O(__name__, ...) PY_TRUFFLE_CEXT_LANDING_NEWREF(__name__, ##__VA_ARGS__)

/* Call function of 'python_cext' module with return type 'PyObject *'; does polyglot cast and error handling */
#define UPCALL_CEXT_BORROWED(__name__, ...) PY_TRUFFLE_CEXT_LANDING_BORROWED(__name__, ##__VA_ARGS__)

/* Call void function of 'python_cext' module; no polyglot cast and no error handling */
#define UPCALL_CEXT_VOID(__name__, ...) ((void)PY_TRUFFLE_CEXT_LANDING_BORROWED(__name__, ##__VA_ARGS__))

/* Call function of 'python_cext' module with return type 'PyObject*'; no polyglot cast but error handling */
#define UPCALL_CEXT_NOCAST(__name__, ...) PY_TRUFFLE_CEXT_LANDING_BORROWED(__name__, ##__VA_ARGS__)

/* Call function of 'python_cext' module with return type 'void*'; no polyglot cast and no error handling */
#define UPCALL_CEXT_PTR(__name__, ...) (polyglot_ensure_ptr(PY_TRUFFLE_CEXT_LANDING_PTR(__name__, ##__VA_ARGS__)))

/* Call function of 'python_cext' module with a primitive return; no polyglot cast but error handling */
#define UPCALL_CEXT_P(__name__, ...) (PY_TRUFFLE_CEXT_LANDING_L(__name__, ##__VA_ARGS__))

/* Call function of 'python_cext' module with return type 'int'; no polyglot cast but error handling */
#define UPCALL_CEXT_I(__name__, ...) (polyglot_ensure_i32(UPCALL_CEXT_P(__name__, ##__VA_ARGS__)))

/* Call function of 'python_cext' module with return type 'long'; no polyglot cast but error handling */
#define UPCALL_CEXT_L(__name__, ...) (polyglot_ensure_i64(UPCALL_CEXT_P(__name__, ##__VA_ARGS__)))

/* Call function of 'python_cext' module with return type 'double'; no polyglot cast but error handling */
#define UPCALL_CEXT_D(__name__, ...) (polyglot_ensure_double(PY_TRUFFLE_CEXT_LANDING_D(__name__, ##__VA_ARGS__)))

#define UPCALL_ID(name)                                                 \
    static void* _jls_ ## name;                                         \
    __attribute__((constructor))                                        \
    static void init_upcall_ ## name(void) {                               \
       _jls_ ## name = polyglot_get_member(PY_TRUFFLE_CEXT, polyglot_from_string(#name, SRC_CS)); \
    }

#define UPCALL_TYPED_ID(name, fun_t)                                                                     \
    static fun_t _jls_ ## name;                                                                          \
    __attribute__((constructor))                                                                         \
    static void init_upcall_ ## name(void) {                                                             \
       _jls_ ## name = (fun_t)polyglot_get_member(PY_TRUFFLE_CEXT, polyglot_from_string(#name, SRC_CS)); \
    }

#define as_char_pointer(obj) ((const char*)UPCALL_CEXT_PTR(polyglot_from_string("to_char_pointer", "ascii"), native_to_java(obj)))
#define as_long(obj) ((long)polyglot_as_i64(polyglot_invoke(PY_TRUFFLE_CEXT, "to_long", to_java(obj))))
#define as_long_long(obj) ((long long)polyglot_as_i64(polyglot_invoke(PY_TRUFFLE_CEXT, "PyLong_AsPrimitive", obj, 1, sizeof(long long))))
#define as_unsigned_long_long(obj) ((unsigned long long)polyglot_as_i64(polyglot_invoke(PY_TRUFFLE_CEXT, "PyLong_AsPrimitive", obj, 0, sizeof(unsigned long long))))
#define as_int(obj) ((int)as_long(obj))
#define as_short(obj) ((short)as_long(obj))
#define as_uchar(obj) ((unsigned char)as_long(obj))
#define as_char(obj) ((char)as_long(obj))
#define as_double(obj) polyglot_as_double(polyglot_invoke(PY_TRUFFLE_CEXT, "to_double", to_java(obj)))
#define as_float(obj) ((float)as_double(obj))

typedef void* (*cache_t)(uint64_t);
extern cache_t cache;

typedef PyObject* (*ptr_cache_t)(PyObject *);
typedef PyTypeObject* (*type_ptr_cache_t)(PyTypeObject *, int64_t);
extern ptr_cache_t ptr_cache;
extern ptr_cache_t ptr_cache_stealing;
extern type_ptr_cache_t type_ptr_cache;

typedef int (*alloc_upcall_fun_t)(void *, Py_ssize_t);
extern alloc_upcall_fun_t alloc_upcall;

typedef int (*free_upcall_fun_t)(void *);
extern free_upcall_fun_t free_upcall;

// Heuristic to test if some value is a pointer object
// TODO we need a reliable solution for that
#define IS_POINTER(__val__) (polyglot_is_value(__val__) && !polyglot_fits_in_i64(__val__))

#define resolve_handle_cached(__cache__, __addr__) (__cache__)(__addr__)

void initialize_type_structure(PyTypeObject* structure, PyTypeObject* ptype, polyglot_typeid tid);

void register_native_slots(PyTypeObject* managed_class, PyGetSetDef* getsets, PyMemberDef* members);


MUST_INLINE
PyObject* native_to_java(PyObject* obj) {
    if (points_to_handle_space(obj)) {
        return resolve_handle_cached(cache, (uint64_t)obj);
    }
    return ptr_cache(obj);
}

MUST_INLINE
PyObject* native_to_java_stealing(PyObject* obj) {
    if (points_to_handle_space(obj)) {
        return resolve_handle_cached(cache, (uint64_t)obj);
    }
    return ptr_cache_stealing(obj);
}


MUST_INLINE
PyTypeObject* native_type_to_java(PyTypeObject* type) {
	if (type == NULL) {
		return NULL;
	}
	if (points_to_handle_space(type)) {
        return (PyTypeObject *)resolve_handle(type);
    }
    return type_ptr_cache(type, Py_REFCNT(type));
}

MUST_INLINE
void* native_pointer_to_java(void* obj) {
    if (points_to_handle_space(obj)) {
        return resolve_handle_cached(cache, (uint64_t)obj);
    }
    return obj;
}

MUST_INLINE
void* function_pointer_to_java(void* obj) {
    if (points_to_handle_space(obj)) {
        return resolve_handle_cached(cache, (uint64_t)obj);
    } else if (!polyglot_is_value(obj)) {
    	return resolve_function(obj);
    }
    return obj;
}

extern void* to_java(PyObject* obj);
extern void* to_java_type(PyTypeObject* cls);
extern PyObject* to_sulong(void *o);

// defined in 'exceptions.c'
void initialize_exceptions();
// defined in 'pyhash.c'
void initialize_hashes();

#define JWRAPPER_DIRECT                      1
#define JWRAPPER_FASTCALL                    2
#define JWRAPPER_FASTCALL_WITH_KEYWORDS      3
#define JWRAPPER_KEYWORDS                    4
#define JWRAPPER_VARARGS                     5
#define JWRAPPER_NOARGS                      6
#define JWRAPPER_O                           7
#define JWRAPPER_METHOD                      8
#define JWRAPPER_UNSUPPORTED                 9
#define JWRAPPER_ALLOC                       10
#define JWRAPPER_SSIZE_ARG                   JWRAPPER_ALLOC
#define JWRAPPER_GETATTR                     11
#define JWRAPPER_SETATTR                     12
#define JWRAPPER_RICHCMP                     13
#define JWRAPPER_SETITEM                     14
#define JWRAPPER_UNARYFUNC                   15
#define JWRAPPER_BINARYFUNC                  16
#define JWRAPPER_BINARYFUNC_L                17
#define JWRAPPER_BINARYFUNC_R                18
#define JWRAPPER_TERNARYFUNC                 19
#define JWRAPPER_TERNARYFUNC_R               20
#define JWRAPPER_LT                          21
#define JWRAPPER_LE                          22
#define JWRAPPER_EQ                          23
#define JWRAPPER_NE                          24
#define JWRAPPER_GT                          25
#define JWRAPPER_GE                          26
#define JWRAPPER_ITERNEXT                    27
#define JWRAPPER_INQUIRY                     28
#define JWRAPPER_DELITEM                     29
#define JWRAPPER_GETITEM                     30
#define JWRAPPER_GETTER                      31
#define JWRAPPER_SETTER                      32
#define JWRAPPER_INITPROC                    33
#define JWRAPPER_HASHFUNC                    34
#define JWRAPPER_CALL                        35
#define JWRAPPER_SETATTRO                    36
#define JWRAPPER_DESCR_GET                   37
#define JWRAPPER_DESCR_SET                   38
#define JWRAPPER_LENFUNC                     39
#define JWRAPPER_OBJOBJPROC                  40
#define JWRAPPER_OBJOBJARGPROC               41
#define JWRAPPER_NEW                         42
#define JWRAPPER_MP_DELITEM                  43
#define JWRAPPER_STR                         44
#define JWRAPPER_REPR                        45

#define TDEBUG __builtin_debugtrap()

static inline int get_method_flags_wrapper(int flags) {
    if (flags < 0)
        return JWRAPPER_DIRECT;
    if ((flags & (METH_FASTCALL | METH_KEYWORDS | METH_METHOD)) == (METH_FASTCALL | METH_KEYWORDS | METH_METHOD))
        return JWRAPPER_METHOD;
    if ((flags & (METH_FASTCALL | METH_KEYWORDS)) == (METH_FASTCALL | METH_KEYWORDS))
        return JWRAPPER_FASTCALL_WITH_KEYWORDS;
    if (flags & METH_FASTCALL)
        return JWRAPPER_FASTCALL;
    if (flags & METH_KEYWORDS)
        return JWRAPPER_KEYWORDS;
    if (flags & METH_VARARGS)
        return JWRAPPER_VARARGS;
    if (flags & METH_NOARGS)
        return JWRAPPER_NOARGS;
    if (flags & METH_O)
        return JWRAPPER_O;
    return JWRAPPER_UNSUPPORTED;
}

#define PY_TRUFFLE_TYPE_GENERIC(__TYPE_NAME__, __SUPER_TYPE__, __FLAGS__, __SIZE__, __ITEMSIZE__, __ALLOC__, __DEALLOC__, __FREE__, __VCALL_OFFSET__) {\
    PyVarObject_HEAD_INIT((__SUPER_TYPE__), 0)\
    __TYPE_NAME__,                              /* tp_name */\
    (__SIZE__),                                 /* tp_basicsize */\
    (__ITEMSIZE__),                             /* tp_itemsize */\
    (__DEALLOC__),                              /* tp_dealloc */\
    (__VCALL_OFFSET__),                         /* tp_vectorcall_offset */\
    0,                                          /* tp_getattr */\
    0,                                          /* tp_setattr */\
    0,                                          /* tp_reserved */\
    0,                                          /* tp_repr */\
    0,                                          /* tp_as_number */\
    0,                                          /* tp_as_sequence */\
    0,                                          /* tp_as_mapping */\
    0,                                          /* tp_hash */\
    0,                                          /* tp_call */\
    0,                                          /* tp_str */\
    0,                                          /* tp_getattro */\
    0,                                          /* tp_setattro */\
    0,                                          /* tp_as_buffer */\
    (__FLAGS__),                                /* tp_flags */\
    0,                                          /* tp_doc */\
    0,                                          /* tp_traverse */\
    0,                                          /* tp_clear */\
    0,                                          /* tp_richcompare */\
    0,                                          /* tp_weaklistoffset */\
    0,                                          /* tp_iter */\
    0,                                          /* tp_iternext */\
    0,                                          /* tp_methods */\
    0,                                          /* tp_members */\
    0,                                          /* tp_getset */\
    0,                                          /* tp_base */\
    0,                                          /* tp_dict */\
    0,                                          /* tp_descr_get */\
    0,                                          /* tp_descr_set */\
    0,                                          /* tp_dictoffset */\
    0,                                          /* tp_init */\
    (__ALLOC__),                                /* tp_alloc */\
    0,                                          /* tp_new */\
    (__FREE__),                                 /* tp_free */\
    0,                                          /* tp_is_gc */\
}

#define PY_TRUFFLE_TYPE_WITH_VECTORCALL(__TYPE_NAME__, __SUPER_TYPE__, __FLAGS__, __SIZE__, __VCALL_OFFSET__) PY_TRUFFLE_TYPE_GENERIC(__TYPE_NAME__, __SUPER_TYPE__, __FLAGS__, __SIZE__, 0, 0, 0, 0, __VCALL_OFFSET__)
#define PY_TRUFFLE_TYPE_WITH_ALLOC(__TYPE_NAME__, __SUPER_TYPE__, __FLAGS__, __SIZE__, __ALLOC__, __DEALLOC__, __FREE__) PY_TRUFFLE_TYPE_GENERIC(__TYPE_NAME__, __SUPER_TYPE__, __FLAGS__, __SIZE__, 0, __ALLOC__, __DEALLOC__, __FREE__, 0)
#define PY_TRUFFLE_TYPE(__TYPE_NAME__, __SUPER_TYPE__, __FLAGS__, __SIZE__) PY_TRUFFLE_TYPE_GENERIC(__TYPE_NAME__, __SUPER_TYPE__, __FLAGS__, __SIZE__, 0, 0, 0, 0, 0)
#define PY_TRUFFLE_TYPE_WITH_ITEMSIZE(__TYPE_NAME__, __SUPER_TYPE__, __FLAGS__, __SIZE__, __ITEMSIZE__) PY_TRUFFLE_TYPE_GENERIC(__TYPE_NAME__, __SUPER_TYPE__, __FLAGS__, __SIZE__, __ITEMSIZE__, 0, 0, 0, 0)

/** to be used from Java code only; returns a type's basic size */
#define BASICSIZE_GETTER(__typename__)extern Py_ssize_t get_ ## __typename__ ## _basicsize() { \
	return sizeof(__typename__); \
} \


int PyTruffle_Debug(void *arg);

extern PyObject marker_struct;
extern PyObject* wrapped_null;

/* An error marker object.
 * The object should not be converted to_java and is intended to be returned in the error case.
 * That's mainly useful for direct calls (without landing functions) of 'python_cext' functions. */
#define ERROR_MARKER wrapped_null

/* internal functions to avoid unnecessary managed <-> native conversions */

/* BYTES, BYTEARRAY */
int bytes_buffer_getbuffer(PyBytesObject *self, Py_buffer *view, int flags);
int bytearray_getbuffer(PyByteArrayObject *obj, Py_buffer *view, int flags);
void bytearray_releasebuffer(PyByteArrayObject *obj, Py_buffer *view);

/* Like 'memcpy' but can read/write from/to managed objects. */
int bytes_copy2mem(char* target, char* source, size_t nbytes);

/* MEMORYVIEW, BUFFERDECORATOR */
int bufferdecorator_getbuffer(PyBufferDecorator *self, Py_buffer *view, int flags);
int memoryview_getbuffer(PyMemoryViewObject *self, Py_buffer *view, int flags);
void memoryview_releasebuffer(PyMemoryViewObject *self, Py_buffer *view);

typedef PyObject* PyObjectPtr;
POLYGLOT_DECLARE_TYPE(PyObjectPtr);

typedef int (*setitem_fun_t)(PyObject*, Py_ssize_t, PyObject*);

#endif
