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
#include "pycore_object.h"

// taken from CPython "Objects/typeobject.c"
typedef struct {
    PyObject_HEAD
    PyTypeObject *type;
    PyObject *obj;
    PyTypeObject *obj_type;
} superobject;

typedef struct PySlot_Offset {
    short subslot_offset;
    short slot_offset;
} PySlot_Offset;

_Py_IDENTIFIER(__doc__);
_Py_IDENTIFIER(__module__);

static void PyTruffle_Type_AddSlots(PyTypeObject* cls, PyGetSetDef** getsets, uint64_t n_getsets, PyMemberDef** members, uint64_t n_members);

static void object_dealloc(PyObject *self) {
    Py_TYPE(self)->tp_free(self);
}

PyTypeObject PyType_Type = {
    PyVarObject_HEAD_INIT(&PyType_Type, 0)
    "type",                                     /* tp_name */
    sizeof(PyHeapTypeObject),                   /* tp_basicsize */
    sizeof(PyMemberDef),                        /* tp_itemsize */
    0,                                          /* tp_dealloc */
    0,                                          /* tp_print */
    0,                                          /* tp_getattr */
    0,                                          /* tp_setattr */
    0,                                          /* tp_reserved */
    0,                                          /* tp_repr */
    0,                                          /* tp_as_number */
    0,                                          /* tp_as_sequence */
    0,                                          /* tp_as_mapping */
    0,                                          /* tp_hash */
    0,                                          /* tp_call */
    0,                                          /* tp_str */
    0,                                          /* tp_getattro */
    0,                                          /* tp_setattro */
    0,                                          /* tp_as_buffer */
    Py_TPFLAGS_DEFAULT | Py_TPFLAGS_HAVE_GC |
        Py_TPFLAGS_BASETYPE | Py_TPFLAGS_TYPE_SUBCLASS,         /* tp_flags */
    0,                                          /* tp_doc */
    0,                                          /* tp_traverse */
    0,                                          /* tp_clear */
    0,                                          /* tp_richcompare */
    0,                                          /* tp_weaklistoffset */
    0,                                          /* tp_iter */
    0,                                          /* tp_iternext */
    0,                                          /* tp_methods */
    0,                                          /* tp_members */
    0,                                          /* tp_getset */
    0,                                          /* tp_base */
    0,                                          /* tp_dict */
    0,                                          /* tp_descr_get */
    0,                                          /* tp_descr_set */
    0,                                          /* tp_dictoffset */
    0,                                          /* tp_init */
    0,                                          /* tp_alloc */
    0,                                          /* tp_new */
    0,                                          /* tp_free */
    0,                                          /* tp_is_gc */
};

PyTypeObject PyBaseObject_Type = PY_TRUFFLE_TYPE_WITH_ALLOC("object", &PyType_Type, Py_TPFLAGS_DEFAULT | Py_TPFLAGS_BASETYPE, sizeof(PyObject), PyType_GenericAlloc, object_dealloc, PyObject_Del);
PyTypeObject PySuper_Type = PY_TRUFFLE_TYPE("super", &PyType_Type, Py_TPFLAGS_DEFAULT | Py_TPFLAGS_HAVE_GC | Py_TPFLAGS_BASETYPE, sizeof(superobject));

typedef int (*type_issubtype_fun_t)(PyTypeObject*, PyTypeObject*);
UPCALL_TYPED_ID(PyType_IsSubtype, type_issubtype_fun_t);
int PyType_IsSubtype(PyTypeObject* a, PyTypeObject* b) {
    return _jls_PyType_IsSubtype(a, b);
}

PyObject* PyType_GenericAlloc(PyTypeObject* cls, Py_ssize_t nitems) {
    PyObject *obj;
    const size_t size = _PyObject_VAR_SIZE(cls, nitems+1);

    if (PyType_IS_GC(cls))
        obj = _PyObject_GC_Malloc(size);
    else
        obj = (PyObject *)PyObject_MALLOC(size);

    if (obj == NULL)
        return PyErr_NoMemory();

    memset(obj, '\0', size);

    if (cls->tp_flags & Py_TPFLAGS_HEAPTYPE)
        Py_INCREF(cls);

    if (cls->tp_itemsize == 0)
        (void)PyObject_INIT(obj, cls);
    else
        (void) PyObject_INIT_VAR((PyVarObject *)obj, cls, nitems);

    return obj;
}

PyObject* PyType_GenericNew(PyTypeObject* cls, PyObject* args, PyObject* kwds) {
    PyObject* newInstance = cls->tp_alloc(cls, 0);
    // TODO(fa): CPython does not do it here; verify if that's correct
    Py_SET_TYPE(newInstance, cls);
    return newInstance;
}

static int add_subclass(PyTypeObject *base, PyTypeObject *type) {
    void* key = (void *) type;
    if (key == NULL) {
        return -1;
    }
    PyObject *dict = base->tp_subclasses;
    if (dict == NULL) {
        base->tp_subclasses = dict = PyDict_New();
        if (dict == NULL) {
            return -1;
        }
    }
    // TODO value should be a weak reference !
    return PyDict_SetItem(base->tp_subclasses, key, (PyObject*)type);
}

/*
 * finds the beginning of the docstring's introspection signature.
 * if present, returns a pointer pointing to the first '('.
 * otherwise returns NULL.
 *
 * doesn't guarantee that the signature is valid, only that it
 * has a valid prefix.  (the signature must also pass skip_signature.)
 */
static const char *
find_signature(const char *name, const char *doc)
{
    const char *dot;
    size_t length;

    if (!doc)
        return NULL;

    assert(name != NULL);

    /* for dotted names like classes, only use the last component */
    dot = strrchr(name, '.');
    if (dot)
        name = dot + 1;

    length = strlen(name);
    if (strncmp(doc, name, length))
        return NULL;
    doc += length;
    if (*doc != '(')
        return NULL;
    return doc;
}

typedef void (*trace_type_fun_t)(PyTypeObject* type, void* type_name);
UPCALL_TYPED_ID(PyTruffle_Trace_Type, trace_type_fun_t);
static void pytruffle_trace_type(PyTypeObject* type) {
    _jls_PyTruffle_Trace_Type(type, type->tp_name != NULL ? polyglot_from_string(type->tp_name, SRC_CS) : NULL);
}

#define SIGNATURE_END_MARKER         ")\n--\n\n"
#define SIGNATURE_END_MARKER_LENGTH  6
/*
 * skips past the end of the docstring's instrospection signature.
 * (assumes doc starts with a valid signature prefix.)
 */
static const char *
skip_signature(const char *doc)
{
    while (*doc) {
        if ((*doc == *SIGNATURE_END_MARKER) &&
            !strncmp(doc, SIGNATURE_END_MARKER, SIGNATURE_END_MARKER_LENGTH))
            return doc + SIGNATURE_END_MARKER_LENGTH;
        if ((*doc == '\n') && (doc[1] == '\n'))
            return NULL;
        doc++;
    }
    return NULL;
}

static const char *
_PyType_DocWithoutSignature(const char *name, const char *internal_doc)
{
    const char *doc = find_signature(name, internal_doc);

    if (doc) {
        doc = skip_signature(doc);
        if (doc)
            return doc;
        }
    return internal_doc;
}

UPCALL_ID(PyTruffle_Type_Modified);
void PyType_Modified(PyTypeObject* type) {
	UPCALL_CEXT_VOID(_jls_PyTruffle_Type_Modified, native_type_to_java(type), polyglot_from_string(type->tp_name, SRC_CS), native_to_java(type->tp_mro));
}

static void inherit_special(PyTypeObject *type, PyTypeObject *base) {

    /* Copying basicsize is connected to the GC flags */
    if (!(type->tp_flags & Py_TPFLAGS_HAVE_GC) &&
        (base->tp_flags & Py_TPFLAGS_HAVE_GC) &&
        (!type->tp_traverse && !type->tp_clear)) {
        type->tp_flags |= Py_TPFLAGS_HAVE_GC;
        if (type->tp_traverse == NULL)
            type->tp_traverse = base->tp_traverse;
        if (type->tp_clear == NULL)
            type->tp_clear = base->tp_clear;
    }
    {
        /* The condition below could use some explanation.
           It appears that tp_new is not inherited for static types
           whose base class is 'object'; this seems to be a precaution
           so that old extension types don't suddenly become
           callable (object.__new__ wouldn't insure the invariants
           that the extension type's own factory function ensures).
           Heap types, of course, are under our control, so they do
           inherit tp_new; static extension types that specify some
           other built-in type as the default also
           inherit object.__new__. */
        if (base != &PyBaseObject_Type ||
            (type->tp_flags & Py_TPFLAGS_HEAPTYPE)) {
            if (type->tp_new == NULL)
                type->tp_new = base->tp_new;
        }
    }
    if (type->tp_basicsize == 0)
        type->tp_basicsize = base->tp_basicsize;

    /* Copy other non-function slots */

#undef COPYVAL
#define COPYVAL(SLOT) \
    if (type->SLOT == 0) type->SLOT = base->SLOT

    COPYVAL(tp_itemsize);
    COPYVAL(tp_weaklistoffset);
    COPYVAL(tp_dictoffset);

    /* Setup fast subclass flags */
    if (PyType_IsSubtype(base, (PyTypeObject*)PyExc_BaseException))
        type->tp_flags |= Py_TPFLAGS_BASE_EXC_SUBCLASS;
    else if (PyType_IsSubtype(base, &PyType_Type))
        type->tp_flags |= Py_TPFLAGS_TYPE_SUBCLASS;
    else if (PyType_IsSubtype(base, &PyLong_Type))
        type->tp_flags |= Py_TPFLAGS_LONG_SUBCLASS;
    else if (PyType_IsSubtype(base, &PyBytes_Type))
        type->tp_flags |= Py_TPFLAGS_BYTES_SUBCLASS;
    else if (PyType_IsSubtype(base, &PyUnicode_Type))
        type->tp_flags |= Py_TPFLAGS_UNICODE_SUBCLASS;
    else if (PyType_IsSubtype(base, &PyTuple_Type))
        type->tp_flags |= Py_TPFLAGS_TUPLE_SUBCLASS;
    else if (PyType_IsSubtype(base, &PyList_Type))
        type->tp_flags |= Py_TPFLAGS_LIST_SUBCLASS;
    else if (PyType_IsSubtype(base, &PyDict_Type))
        type->tp_flags |= Py_TPFLAGS_DICT_SUBCLASS;
    if (PyType_HasFeature(base, _Py_TPFLAGS_MATCH_SELF)) {
        type->tp_flags |= _Py_TPFLAGS_MATCH_SELF;
    }
}

static void inherit_slots(PyTypeObject *type, PyTypeObject *base) {
    PyTypeObject *basebase;

#undef SLOTDEFINED
#undef COPYSLOT
#undef COPYNUM
#undef COPYSEQ
#undef COPYMAP
#undef COPYBUF

#define SLOTDEFINED(SLOT) \
    (base->SLOT != 0 && \
     (basebase == NULL || base->SLOT != basebase->SLOT))

#define COPYSLOT(SLOT) \
    if (!type->SLOT && SLOTDEFINED(SLOT)) type->SLOT = base->SLOT

#define COPYASYNC(SLOT) COPYSLOT(tp_as_async->SLOT)
#define COPYNUM(SLOT) COPYSLOT(tp_as_number->SLOT)
#define COPYSEQ(SLOT) COPYSLOT(tp_as_sequence->SLOT)
#define COPYMAP(SLOT) COPYSLOT(tp_as_mapping->SLOT)
#define COPYBUF(SLOT) COPYSLOT(tp_as_buffer->SLOT)

    if (type->tp_as_buffer != NULL && base->tp_as_buffer != NULL) {
        basebase = base->tp_base;
        if (basebase->tp_as_buffer == NULL)
            basebase = NULL;
        COPYBUF(bf_getbuffer);
        COPYBUF(bf_releasebuffer);
    }

    basebase = base->tp_base;

    COPYSLOT(tp_dealloc);
    if (type->tp_getattr == NULL && type->tp_getattro == NULL) {
        type->tp_getattr = base->tp_getattr;
        type->tp_getattro = base->tp_getattro;
    }
    if (type->tp_setattr == NULL && type->tp_setattro == NULL) {
        type->tp_setattr = base->tp_setattr;
        type->tp_setattro = base->tp_setattro;
    }
    {
        /* Always inherit tp_vectorcall_offset to support PyVectorcall_Call().
         * If _Py_TPFLAGS_HAVE_VECTORCALL is not inherited, then vectorcall
         * won't be used automatically. */
        COPYSLOT(tp_vectorcall_offset);

        /* Inherit _Py_TPFLAGS_HAVE_VECTORCALL for non-heap types
        * if tp_call is not overridden */
        if (!type->tp_call &&
            (base->tp_flags & _Py_TPFLAGS_HAVE_VECTORCALL) &&
            !(type->tp_flags & Py_TPFLAGS_HEAPTYPE))
        {
            type->tp_flags |= _Py_TPFLAGS_HAVE_VECTORCALL;
        }
        /* COPYSLOT(tp_call); */
    }
    {
        COPYSLOT(tp_iter);
        COPYSLOT(tp_iternext);
    }

    if ((type->tp_flags & Py_TPFLAGS_HAVE_FINALIZE) &&
        (base->tp_flags & Py_TPFLAGS_HAVE_FINALIZE)) {
        COPYSLOT(tp_finalize);
    }
    if ((type->tp_flags & Py_TPFLAGS_HAVE_GC) ==
        (base->tp_flags & Py_TPFLAGS_HAVE_GC)) {
        /* They agree about gc. */
        COPYSLOT(tp_free);
    }
    else if ((type->tp_flags & Py_TPFLAGS_HAVE_GC) &&
             type->tp_free == NULL &&
             base->tp_free == PyObject_Free) {
        /* A bit of magic to plug in the correct default
         * tp_free function when a derived class adds gc,
         * didn't define tp_free, and the base uses the
         * default non-gc tp_free.
         */
        type->tp_free = PyObject_GC_Del;
    }
    /* else they didn't agree about gc, and there isn't something
     * obvious to be done -- the type is on its own.
     */
}

typedef int (*add_member_fun_t)(void *, void *, void *, int32_t, Py_ssize_t, int32_t, char *);
UPCALL_TYPED_ID(AddMember, add_member_fun_t);
static int add_member(PyTypeObject* cls, PyObject* type_dict, PyObject* mname, int mtype, Py_ssize_t moffset, int mflags, char* mdoc) {
    // TODO support member flags other than READONLY
    return _jls_AddMember( cls, native_to_java(type_dict), native_to_java(mname), mtype, moffset, (mflags & READONLY) == 0, mdoc);
}

typedef int (*add_getset_fun_t)(PyTypeObject *, PyObject *, void *, void *, void *, char *, void *);
UPCALL_TYPED_ID(AddGetSet, add_getset_fun_t);
int add_getset(PyTypeObject* cls, PyObject* type_dict, char* name, getter getter_fun, setter setter_fun, char* doc, void* closure) {
    /* do not convert the closure, it is handed to the getter and setter as-is */
    return _jls_AddGetSet(cls,
                        native_to_java(type_dict),
                        polyglot_from_string(name, SRC_CS),
                        getter_fun != NULL ? function_pointer_to_java(getter_fun) : NULL,
                        setter_fun != NULL ? function_pointer_to_java(setter_fun) : NULL,
                        doc,
                        closure);
}

//                                     method_def     cls             dict        name          cfunc   flags sig  doc
typedef int (*AddFunctionToType_fun_t)(PyMethodDef *, PyTypeObject *, PyObject *, const char *, void *, int , int, char *);
UPCALL_TYPED_ID(AddFunctionToType, AddFunctionToType_fun_t);
static void add_method(PyTypeObject* cls, PyObject* type_dict, PyMethodDef* def) {
        _jls_AddFunctionToType(def,
                       cls,
                       native_to_java(type_dict),
                       polyglot_from_string(def->ml_name, SRC_CS),
                       function_pointer_to_java(def->ml_meth),
                       def->ml_flags,
                       get_method_flags_wrapper(def->ml_flags),
                       def->ml_doc);
}

typedef int (*add_slot_fun_t)(PyTypeObject *, PyObject *, void *, void *, int , int, char *);
UPCALL_TYPED_ID(add_slot, add_slot_fun_t);
static void add_slot(PyTypeObject* cls, PyObject* type_dict, char* name, void* meth, int flags, int signature, char* doc) {
    if (meth) {
        _jls_add_slot(cls,
                native_to_java(type_dict),
                polyglot_from_string(name, SRC_CS),
                function_pointer_to_java(meth),
                flags,
                (signature != 0 ? signature : get_method_flags_wrapper(flags)),
                doc);
    }
}

#define ADD_MEMBER(__javacls__, __tpdict__, __mname__, __mtype__, __moffset__, __mflags__, __mdoc__)     \
    add_member((__javacls__), (__tpdict__), (__mname__), (__mtype__), (__moffset__), (__mflags__), (__mdoc__))


#define ADD_GETSET(__javacls__, __tpdict__, __name__, __getter__, __setter__, __doc__, __closure__)     \
    add_getset((__javacls__), (__tpdict__), (__name__), (__getter__), (__setter__), (__doc__), (__closure__))


UPCALL_ID(PyTruffle_Get_Inherited_Native_Slots);
UPCALL_ID(PyTruffle_Compute_Mro);
UPCALL_ID(PyTruffle_NewTypeDict);
int PyType_Ready(PyTypeObject* cls) {
#define RETURN_ERROR(__type__) \
    do { \
      	(__type__)->tp_flags &= ~Py_TPFLAGS_READYING; \
      	Py_DECREF((__type__)); \
        return -1; \
	} while(0)

#define ADD_IF_MISSING(attr, def) if (!(attr)) { attr = def; }
#define ADD_SLOT_CONV(__name__, __meth__, __flags__, __signature__) add_slot(cls, dict, (__name__), (__meth__), (__flags__), (__signature__), NULL)

    Py_ssize_t n;
    Py_ssize_t i;

    // https://docs.python.org/3/c-api/typeobj.html#Py_TPFLAGS_READY
    if ((cls->tp_flags & Py_TPFLAGS_READY) || (cls->tp_flags & Py_TPFLAGS_READYING)) {
        return 0;
    }
    cls->tp_flags = cls->tp_flags | Py_TPFLAGS_READYING;

    /* Types are often just static mem; so register them to be able to rule out invalid accesses.  */
    if(PyTruffle_Trace_Memory()) {
        pytruffle_trace_type(cls);
    }

    /* IMPORTANT: This is a Truffle-specific statement. Since the refcnt for the type is currently 0 and
       we will create several references to this object that will be collected during the execution of
       this method, we need to keep it alive. */
    Py_INCREF(cls);


    PyTypeObject* base;

    /* Initialize tp_base (defaults to BaseObject unless that's us) */
    base = cls->tp_base;
    if (base == NULL && cls != &PyBaseObject_Type) {
        base = cls->tp_base = &PyBaseObject_Type;
        Py_INCREF(base);
    }

    /* Now the only way base can still be NULL is if type is
     * &PyBaseObject_Type.
     */

    /* Initialize the base class */
    if (base != NULL && !(base->tp_flags & Py_TPFLAGS_READY)) {
        if (PyType_Ready(base) < 0) {
        	RETURN_ERROR(cls);
        }
    }

    /* Initialize ob_type if NULL.      This means extensions that want to be
       compilable separately on Windows can call PyType_Ready() instead of
       initializing the ob_type field of their type objects. */
    /* The test for base != NULL is really unnecessary, since base is only
       NULL when type is &PyBaseObject_Type, and we know its ob_type is
       not NULL (it's initialized to &PyType_Type).      But coverity doesn't
       know that. */
    if (Py_TYPE(cls) == NULL && base != NULL) {
        Py_SET_TYPE(cls, Py_TYPE(base));
    }


    /* Initialize tp_bases */
    PyObject* bases = cls->tp_bases;
    if (bases == NULL) {
        if (base == NULL) {
            bases = PyTuple_New(0);
        } else {
            bases = PyTuple_Pack(1, base);
        }
        cls->tp_bases = bases;
    }

    /* Initialize tp_dict */
    PyObject* dict = cls->tp_dict;
    if (dict == NULL) {
        dict = UPCALL_CEXT_O(_jls_PyTruffle_NewTypeDict, native_type_to_java(cls));
        if (dict == NULL) {
        	RETURN_ERROR(cls);
        }
        cls->tp_dict = dict;
    }

    if (cls->tp_methods) {
        for (PyMethodDef* def = cls->tp_methods; def->ml_name != NULL; def++) {
            add_method(cls, dict, def);
        }
    }

    PyMemberDef* members = cls->tp_members;
    if (members) {
        int i = 0;
        PyMemberDef member = members[i];
        while (member.name != NULL) {
            ADD_MEMBER(cls, dict, polyglot_from_string(member.name, SRC_CS), member.type, member.offset, member.flags, member.doc);
            member = members[++i];
        }
    }

    PyGetSetDef* getsets = cls->tp_getset;
    if (getsets) {
        int i = 0;
        PyGetSetDef getset = getsets[i];
        while (getset.name != NULL) {
        	ADD_GETSET(cls, dict, getset.name, getset.get, getset.set, getset.doc, getset.closure);
            getset = getsets[++i];
        }
    }

    /* initialize mro */
    cls->tp_mro = UPCALL_CEXT_O(_jls_PyTruffle_Compute_Mro, cls, polyglot_from_string(cls->tp_name, SRC_CS));

    /* Inherit special flags from dominant base */
    if (cls->tp_base != NULL)
        inherit_special(cls, cls->tp_base);

    /* Initialize tp_dict properly */
    bases = native_pointer_to_java(cls->tp_mro);
    assert(bases != NULL);
    assert(PyTuple_Check(bases));
    n = PyTuple_GET_SIZE(bases);
    for (i = 1; i < n; i++) {
        PyObject *b = PyTuple_GET_ITEM(bases, i);
        if (PyType_Check(b))
            inherit_slots(cls, (PyTypeObject *)b);
    }

    ADD_IF_MISSING(cls->tp_alloc, PyType_GenericAlloc);
    ADD_IF_MISSING(cls->tp_new, PyType_GenericNew);

    // add special methods defined directly on the type structs
    ADD_SLOT_CONV("__dealloc__", cls->tp_dealloc, -1, JWRAPPER_DIRECT);
    // https://docs.python.org/3/c-api/typeobj.html#c.PyTypeObject.tp_getattr
    // tp_getattr and tp_setattr are deprecated, and should be the same as
    // tp_getattro and tp_setattro

    // NOTE: The slots may be called from managed code, i.e., we need to wrap the functions
    // and convert arguments that should be C primitives.
    ADD_SLOT_CONV("__getattr__", cls->tp_getattr, -2, JWRAPPER_GETATTR);
    ADD_SLOT_CONV("__setattr__", cls->tp_setattr, -3, JWRAPPER_SETATTR);
    ADD_SLOT_CONV("__repr__", cls->tp_repr, -1, JWRAPPER_REPR);
    ADD_SLOT_CONV("__hash__", cls->tp_hash, -1, JWRAPPER_HASHFUNC);
    ADD_SLOT_CONV("__call__", cls->tp_call, METH_KEYWORDS | METH_VARARGS, JWRAPPER_CALL);
    ADD_SLOT_CONV("__str__", cls->tp_str, -1, JWRAPPER_STR);
    ADD_SLOT_CONV("__getattr__", cls->tp_getattro, -2, JWRAPPER_DIRECT);
    ADD_SLOT_CONV("__setattr__", cls->tp_setattro, -3, JWRAPPER_SETATTRO);
    ADD_SLOT_CONV("__clear__", cls->tp_clear, -1, JWRAPPER_INQUIRY);

    /* IMPORTANT NOTE: If the class already provides 'tp_richcompare' but this is the default
       'object.__truffle_richcompare__' function, then we need to break a recursive cycle since
       the default function dispatches to the individual comparison functions which would in
       this case again invoke 'object.__truffle_richcompare__'. */
    if (cls->tp_richcompare && cls->tp_richcompare != PyBaseObject_Type.tp_richcompare) {
        ADD_SLOT_CONV("__compare__", cls->tp_richcompare, -3, JWRAPPER_RICHCMP);
        ADD_SLOT_CONV("__lt__", cls->tp_richcompare, -2, JWRAPPER_LT);
        ADD_SLOT_CONV("__le__", cls->tp_richcompare, -2, JWRAPPER_LE);
        ADD_SLOT_CONV("__eq__", cls->tp_richcompare, -2, JWRAPPER_EQ);
        ADD_SLOT_CONV("__ne__", cls->tp_richcompare, -2, JWRAPPER_NE);
        ADD_SLOT_CONV("__gt__", cls->tp_richcompare, -2, JWRAPPER_GT);
        ADD_SLOT_CONV("__ge__", cls->tp_richcompare, -2, JWRAPPER_GE);
    }
    ADD_SLOT_CONV("__iter__", cls->tp_iter, -1, JWRAPPER_UNARYFUNC);
    ADD_SLOT_CONV("__next__", cls->tp_iternext, -1, JWRAPPER_ITERNEXT);
    ADD_SLOT_CONV("__get__", cls->tp_descr_get, -3, JWRAPPER_DESCR_GET);
    ADD_SLOT_CONV("__set__", cls->tp_descr_set, -3, JWRAPPER_DESCR_SET);
    ADD_SLOT_CONV("__init__", cls->tp_init, METH_KEYWORDS | METH_VARARGS, JWRAPPER_INITPROC);
    ADD_SLOT_CONV("__alloc__", cls->tp_alloc, -2, JWRAPPER_ALLOC);
    ADD_SLOT_CONV("__new__", cls->tp_new, METH_KEYWORDS | METH_VARARGS, JWRAPPER_NEW);
    ADD_SLOT_CONV("__free__", cls->tp_free, -1, JWRAPPER_DIRECT);
    ADD_SLOT_CONV("__del__", cls->tp_del, -1, JWRAPPER_DIRECT);
    ADD_SLOT_CONV("__finalize__", cls->tp_finalize, -1, JWRAPPER_DIRECT);

    PySequenceMethods* sequences = native_pointer_to_java(cls->tp_as_sequence);
    if (sequences) {
    	// sequence functions first, so that the number functions take precendence
        ADD_SLOT_CONV("__len__", sequences->sq_length, -1, JWRAPPER_LENFUNC);
        ADD_SLOT_CONV("__add__", sequences->sq_concat, -2, JWRAPPER_BINARYFUNC);
        ADD_SLOT_CONV("__mul__", sequences->sq_repeat, -2, JWRAPPER_SSIZE_ARG);
        ADD_SLOT_CONV("__getitem__", sequences->sq_item, -2, JWRAPPER_GETITEM);
        ADD_SLOT_CONV("__setitem__", sequences->sq_ass_item, -3, JWRAPPER_SETITEM);
        ADD_SLOT_CONV("__delitem__", sequences->sq_ass_item, -3, JWRAPPER_DELITEM);
        ADD_SLOT_CONV("__contains__", sequences->sq_contains, -2, JWRAPPER_OBJOBJPROC);
        ADD_SLOT_CONV("__iadd__", sequences->sq_inplace_concat, -2, JWRAPPER_BINARYFUNC);
        ADD_SLOT_CONV("__imul__", sequences->sq_inplace_repeat, -2, JWRAPPER_SSIZE_ARG);
    }

    PyNumberMethods* numbers = native_pointer_to_java(cls->tp_as_number);
    if (numbers) {
        ADD_SLOT_CONV("__add__", numbers->nb_add, -2, JWRAPPER_BINARYFUNC_L);
        ADD_SLOT_CONV("__radd__", numbers->nb_add, -2, JWRAPPER_BINARYFUNC_R);
        ADD_SLOT_CONV("__sub__", numbers->nb_subtract, -2, JWRAPPER_BINARYFUNC_L);
        ADD_SLOT_CONV("__rsub__", numbers->nb_subtract, -2, JWRAPPER_BINARYFUNC_R);
        ADD_SLOT_CONV("__mul__", numbers->nb_multiply, -2, JWRAPPER_BINARYFUNC_L);
        ADD_SLOT_CONV("__rmul__", numbers->nb_multiply, -2, JWRAPPER_BINARYFUNC_R);
        ADD_SLOT_CONV("__mod__", numbers->nb_remainder, -2, JWRAPPER_BINARYFUNC_L);
        ADD_SLOT_CONV("__rmod__", numbers->nb_remainder, -2, JWRAPPER_BINARYFUNC_R);
        ADD_SLOT_CONV("__divmod__", numbers->nb_divmod, -2, JWRAPPER_BINARYFUNC_L);
        ADD_SLOT_CONV("__rdivmod__", numbers->nb_divmod, -2, JWRAPPER_BINARYFUNC_R);
        ADD_SLOT_CONV("__pow__", numbers->nb_power, -3, JWRAPPER_TERNARYFUNC);
        ADD_SLOT_CONV("__rpow__", numbers->nb_power, -3, JWRAPPER_TERNARYFUNC_R);
        ADD_SLOT_CONV("__neg__", numbers->nb_negative, -1, JWRAPPER_UNARYFUNC);
        ADD_SLOT_CONV("__pos__", numbers->nb_positive, -1, JWRAPPER_UNARYFUNC);
        ADD_SLOT_CONV("__abs__", numbers->nb_absolute, -1, JWRAPPER_UNARYFUNC);
        ADD_SLOT_CONV("__bool__", numbers->nb_bool, -1, JWRAPPER_INQUIRY);
        ADD_SLOT_CONV("__invert__", numbers->nb_invert, -1, JWRAPPER_UNARYFUNC);
        ADD_SLOT_CONV("__lshift__", numbers->nb_lshift, -2, JWRAPPER_BINARYFUNC_L);
        ADD_SLOT_CONV("__rlshift__", numbers->nb_lshift, -2, JWRAPPER_BINARYFUNC_R);
        ADD_SLOT_CONV("__rshift__", numbers->nb_rshift, -2, JWRAPPER_BINARYFUNC_L);
        ADD_SLOT_CONV("__rrshift__", numbers->nb_rshift, -2, JWRAPPER_BINARYFUNC_R);
        ADD_SLOT_CONV("__and__", numbers->nb_and, -2, JWRAPPER_BINARYFUNC_L);
        ADD_SLOT_CONV("__rand__", numbers->nb_and, -2, JWRAPPER_BINARYFUNC_R);
        ADD_SLOT_CONV("__xor__", numbers->nb_xor, -2, JWRAPPER_BINARYFUNC_L);
        ADD_SLOT_CONV("__rxor__", numbers->nb_xor, -2, JWRAPPER_BINARYFUNC_R);
        ADD_SLOT_CONV("__or__", numbers->nb_or, -2, JWRAPPER_BINARYFUNC_L);
        ADD_SLOT_CONV("__ror__", numbers->nb_or, -2, JWRAPPER_BINARYFUNC_R);
        ADD_SLOT_CONV("__int__", numbers->nb_int, -1, JWRAPPER_UNARYFUNC);
        ADD_SLOT_CONV("__float__", numbers->nb_float, -1, JWRAPPER_UNARYFUNC);
        ADD_SLOT_CONV("__iadd__", numbers->nb_inplace_add, -2, JWRAPPER_BINARYFUNC);
        ADD_SLOT_CONV("__isub__", numbers->nb_inplace_subtract, -2, JWRAPPER_BINARYFUNC);
        ADD_SLOT_CONV("__imul__", numbers->nb_inplace_multiply, -2, JWRAPPER_BINARYFUNC);
        ADD_SLOT_CONV("__imod__", numbers->nb_inplace_remainder, -2, JWRAPPER_BINARYFUNC);
        ADD_SLOT_CONV("__ipow__", numbers->nb_inplace_power, -3, JWRAPPER_TERNARYFUNC);
        ADD_SLOT_CONV("__ilshift__", numbers->nb_inplace_lshift, -2, JWRAPPER_BINARYFUNC);
        ADD_SLOT_CONV("__irshift__", numbers->nb_inplace_rshift, -2, JWRAPPER_BINARYFUNC);
        ADD_SLOT_CONV("__iand__", numbers->nb_inplace_and, -2, JWRAPPER_BINARYFUNC);
        ADD_SLOT_CONV("__ixor__", numbers->nb_inplace_xor, -2, JWRAPPER_BINARYFUNC);
        ADD_SLOT_CONV("__ior__", numbers->nb_inplace_or, -2, JWRAPPER_BINARYFUNC);
        ADD_SLOT_CONV("__floordiv__", numbers->nb_floor_divide, -2, JWRAPPER_BINARYFUNC_L);
        ADD_SLOT_CONV("__rfloordiv__", numbers->nb_floor_divide, -2, JWRAPPER_BINARYFUNC_R);
        ADD_SLOT_CONV("__truediv__", numbers->nb_true_divide, -2, JWRAPPER_BINARYFUNC_L);
        ADD_SLOT_CONV("__rtruediv__", numbers->nb_true_divide, -2, JWRAPPER_BINARYFUNC_R);
        ADD_SLOT_CONV("__ifloordiv__", numbers->nb_inplace_floor_divide, -2, JWRAPPER_BINARYFUNC);
        ADD_SLOT_CONV("__itruediv__", numbers->nb_inplace_true_divide, -2, JWRAPPER_BINARYFUNC);
        ADD_SLOT_CONV("__index__", numbers->nb_index, -1, JWRAPPER_UNARYFUNC);
        ADD_SLOT_CONV("__matmul__", numbers->nb_matrix_multiply, -2, JWRAPPER_BINARYFUNC_L);
        ADD_SLOT_CONV("__rmatmul__", numbers->nb_matrix_multiply, -2, JWRAPPER_BINARYFUNC_R);
        ADD_SLOT_CONV("__imatmul__", numbers->nb_inplace_matrix_multiply, -2, JWRAPPER_BINARYFUNC_L);
    }

    PyMappingMethods* mappings = native_pointer_to_java(cls->tp_as_mapping);
    if (mappings) {
        ADD_SLOT_CONV("__len__", mappings->mp_length, -1, JWRAPPER_LENFUNC);
        ADD_SLOT_CONV("__getitem__", mappings->mp_subscript, -2, JWRAPPER_BINARYFUNC);
        ADD_SLOT_CONV("__setitem__", mappings->mp_ass_subscript, -3, JWRAPPER_OBJOBJARGPROC);
        ADD_SLOT_CONV("__delitem__", mappings->mp_ass_subscript, -3, JWRAPPER_MP_DELITEM);
    }

    PyAsyncMethods* async = native_pointer_to_java(cls->tp_as_async);
    if (async) {
        ADD_SLOT_CONV("__await__", async->am_await, -1, JWRAPPER_DIRECT);
        ADD_SLOT_CONV("__aiter__", async->am_aiter, -1, JWRAPPER_DIRECT);
        ADD_SLOT_CONV("__anext__", async->am_anext, -1, JWRAPPER_DIRECT);
    }

    PyBufferProcs* buffers = native_pointer_to_java(cls->tp_as_buffer);
    if (buffers) {
        // TODO ...
    }

    // process inherited slots
    // CPython doesn't do that in 'PyType_Ready' but we must because a native type can inherit
    // dynamic slots from a managed Python class. Since the managed Python class may be created
    // when the C API is not loaded, we need to do that later.
    /*
    UPCALL_CEXT_O(_jls_PyTruffle_Type_Slots, native_to_java((PyObject*)cls), native_to_java(cls->tp_dict));
    */
    PyGetSetDef** inherited_getset = (PyGetSetDef**) UPCALL_CEXT_PTR(_jls_PyTruffle_Get_Inherited_Native_Slots, native_to_java((PyObject*)cls), polyglot_from_string("getsets", SRC_CS));
    PyMemberDef** inherited_members = (PyMemberDef**) UPCALL_CEXT_PTR(_jls_PyTruffle_Get_Inherited_Native_Slots, native_to_java((PyObject*)cls), polyglot_from_string("members", SRC_CS));
    uint64_t n_getsets = polyglot_get_array_size(inherited_getset);
    uint64_t n_members = polyglot_get_array_size(inherited_members);
  	PyTruffle_Type_AddSlots(cls, inherited_getset, n_getsets, inherited_members, n_members);

    /* Initialize this classes' tp_subclasses dict. This is necessary because our managed classes won't do. */
    cls->tp_subclasses = PyDict_New();

    /* if the type dictionary doesn't contain a __doc__, set it from
       the tp_doc slot.
     */
    PyObject* doc_id = (PyObject *)polyglot_from_string("__doc__", SRC_CS);
    if (PyDict_GetItem(cls->tp_dict, doc_id) == NULL) {
        if (cls->tp_doc != NULL) {
            const char *old_doc = _PyType_DocWithoutSignature(cls->tp_name, cls->tp_doc);
            PyObject *doc = PyUnicode_FromString(old_doc);
            if (doc == NULL) {
                RETURN_ERROR(cls);
            }
            if (PyDict_SetItem(cls->tp_dict, doc_id, doc) < 0) {
                Py_DECREF(doc);
                RETURN_ERROR(cls);
            }
            Py_DECREF(doc);
        } else if (PyDict_SetItem(cls->tp_dict, doc_id, Py_None) < 0) {
            RETURN_ERROR(cls);
        }
    }

    /* Some more special stuff */
    base = cls->tp_base;
    if (base != NULL) {
        // if (cls->tp_as_async == NULL)
        //     cls->tp_as_async = base->tp_as_async;
        // if (cls->tp_as_number == NULL)
        //     cls->tp_as_number = base->tp_as_number;
        // if (cls->tp_as_sequence == NULL)
        //     cls->tp_as_sequence = base->tp_as_sequence;
        // if (cls->tp_as_mapping == NULL)
        //     cls->tp_as_mapping = base->tp_as_mapping;
        if (cls->tp_as_buffer == NULL)
            cls->tp_as_buffer = base->tp_as_buffer;
    }

    /* Link into each base class's list of subclasses */
    bases = native_pointer_to_java(cls->tp_bases);
    n = PyTuple_GET_SIZE(bases);
    for (i = 0; i < n; i++) {
        PyObject* base_class_object = PyTuple_GetItem(bases, i);
        PyTypeObject* b = (PyTypeObject*) base_class_object;
        if (PyType_Check(b) && add_subclass(b, cls) < 0) {
        	RETURN_ERROR(cls);
        }
    }

    // done
    cls->tp_flags = cls->tp_flags & ~Py_TPFLAGS_READYING;
    cls->tp_flags = cls->tp_flags | Py_TPFLAGS_READY;

    // it may be that the type was used uninitialized
    UPCALL_CEXT_VOID(_jls_PyTruffle_Type_Modified, cls, polyglot_from_string(cls->tp_name, SRC_CS), Py_NoValue);

	// Truffle-specific decref (for reason, see first call to Py_INCREF in this function)
	Py_DECREF(cls);

    return 0;

#undef ADD_IF_MISSING
#undef ADD_SLOT
}

MUST_INLINE static int valid_identifier(PyObject *s) {
    if (!PyUnicode_Check(s)) {
        PyErr_Format(PyExc_TypeError,
                     "__slots__ items must be strings, not '%.200s'",
                     Py_TYPE(s)->tp_name);
        return 0;
    }
    return 1;
}

/* Add get-set descriptors for slots provided in 'getsets' and 'members'. */
static void PyTruffle_Type_AddSlots(PyTypeObject *cls, PyGetSetDef **getsets, uint64_t n_getsets, PyMemberDef **members, uint64_t n_members) {
    const PyObject *dict = cls->tp_dict;
    for (uint64_t j = 0; j < n_getsets; j++) {
        PyGetSetDef *getsets_sub = getsets[j];
        if (getsets_sub) {
            int i = 0;
            PyGetSetDef getset = getsets_sub[i];
            while (getset.name != NULL) {
                ADD_GETSET(cls, dict, getset.name, getset.get, getset.set, getset.doc, getset.closure);
                getset = getsets_sub[++i];
            }
        }
    }
    for (uint64_t j = 0; j < n_getsets; j++) {
        PyMemberDef *members_sub = members[j];
        if (members_sub) {
            int i = 0;
            PyMemberDef member = members_sub[i];
            while (member.name != NULL) {
                ADD_MEMBER(cls, dict, polyglot_from_string(member.name, SRC_CS), member.type, member.offset, member.flags, member.doc);
                member = members_sub[++i];
            }
        }
    }
}

unsigned long PyType_GetFlags(struct _typeobject *type) {
    return type->tp_flags;
}

// taken from CPython "Objects/typeobject.c"
static int
extra_ivars(PyTypeObject *type, PyTypeObject *base)
{
    size_t t_size = type->tp_basicsize;
    size_t b_size = base->tp_basicsize;

    assert(t_size >= b_size); /* Else type smaller than base! */
    if (type->tp_itemsize || base->tp_itemsize) {
        /* If itemsize is involved, stricter rules */
        return t_size != b_size ||
            type->tp_itemsize != base->tp_itemsize;
    }
    if (type->tp_weaklistoffset && base->tp_weaklistoffset == 0 &&
        type->tp_weaklistoffset + sizeof(PyObject *) == t_size &&
        type->tp_flags & Py_TPFLAGS_HEAPTYPE)
        t_size -= sizeof(PyObject *);
    if (type->tp_dictoffset && base->tp_dictoffset == 0 &&
        type->tp_dictoffset + sizeof(PyObject *) == t_size &&
        type->tp_flags & Py_TPFLAGS_HEAPTYPE)
        t_size -= sizeof(PyObject *);

    return t_size != b_size;
}

// taken from CPython "Objects/typeobject.c"
static PyTypeObject *
solid_base(PyTypeObject *type)
{
    PyTypeObject *base;

    if (type->tp_base)
        base = solid_base(type->tp_base);
    else
        base = &PyBaseObject_Type;
    if (extra_ivars(type, base))
        return type;
    else
        return base;
}

// taken from CPython "Objects/typeobject.c"
/* Calculate the best base amongst multiple base classes.
   This is the first one that's on the path to the "solid base". */
static PyTypeObject *
best_base(PyObject *bases)
{
    Py_ssize_t i, n;
    PyTypeObject *base, *winner, *candidate, *base_i;
    PyObject *base_proto;

    assert(PyTuple_Check(bases));
    n = PyTuple_GET_SIZE(bases);
    assert(n > 0);
    base = NULL;
    winner = NULL;
    for (i = 0; i < n; i++) {
        base_proto = PyTuple_GET_ITEM(bases, i);
        if (!PyType_Check(base_proto)) {
            PyErr_SetString(
                PyExc_TypeError,
                "bases must be types");
            return NULL;
        }
        base_i = (PyTypeObject *)base_proto;
        if (!_PyType_IsReady(base_i)) {
            if (PyType_Ready(base_i) < 0)
                return NULL;
        }
        if (!_PyType_HasFeature(base_i, Py_TPFLAGS_BASETYPE)) {
            PyErr_Format(PyExc_TypeError,
                         "type '%.100s' is not an acceptable base type",
                         base_i->tp_name);
            return NULL;
        }
        candidate = solid_base(base_i);
        if (winner == NULL) {
            winner = candidate;
            base = base_i;
        }
        else if (PyType_IsSubtype(winner, candidate))
            ;
        else if (PyType_IsSubtype(candidate, winner)) {
            winner = candidate;
            base = base_i;
        }
        else {
            PyErr_SetString(
                PyExc_TypeError,
                "multiple bases have "
                "instance lay-out conflict");
            return NULL;
        }
    }
    assert (base != NULL);

    return base;
}
// taken from CPython "Objects/typeobject.c"
static const PySlot_Offset pyslot_offsets[] = {
    {0, 0},
#include "typeslots.inc"
};

// taken from CPython "Objects/typeobject.c"
PyObject *
PyType_FromModuleAndSpec(PyObject *module, PyType_Spec *spec, PyObject *bases)
{
    PyHeapTypeObject *res;
    PyObject *modname;
    PyTypeObject *type, *base;
    int r;

    const PyType_Slot *slot;
    Py_ssize_t nmembers, weaklistoffset, dictoffset, vectorcalloffset;
    char *res_start;
    short slot_offset, subslot_offset;

    nmembers = weaklistoffset = dictoffset = vectorcalloffset = 0;
    for (slot = spec->slots; slot->slot; slot++) {
        if (slot->slot == Py_tp_members) {
            nmembers = 0;
            for (const PyMemberDef *memb = slot->pfunc; memb->name != NULL; memb++) {
                nmembers++;
                if (strcmp(memb->name, "__weaklistoffset__") == 0) {
                    // The PyMemberDef must be a Py_ssize_t and readonly
                    assert(memb->type == T_PYSSIZET);
                    assert(memb->flags == READONLY);
                    weaklistoffset = memb->offset;
                }
                if (strcmp(memb->name, "__dictoffset__") == 0) {
                    // The PyMemberDef must be a Py_ssize_t and readonly
                    assert(memb->type == T_PYSSIZET);
                    assert(memb->flags == READONLY);
                    dictoffset = memb->offset;
                }
                if (strcmp(memb->name, "__vectorcalloffset__") == 0) {
                    // The PyMemberDef must be a Py_ssize_t and readonly
                    assert(memb->type == T_PYSSIZET);
                    assert(memb->flags == READONLY);
                    vectorcalloffset = memb->offset;
                }
            }
        }
    }

    res = (PyHeapTypeObject*)PyType_GenericAlloc(&PyType_Type, nmembers);
    if (res == NULL)
        return NULL;
    res_start = (char*)res;

    if (spec->name == NULL) {
        PyErr_SetString(PyExc_SystemError,
                        "Type spec does not define the name field.");
        goto fail;
    }

    /* Set the type name and qualname */
    const char *s = strrchr(spec->name, '.');
    if (s == NULL)
        s = spec->name;
    else
        s++;

    type = &res->ht_type;
    /* The flags must be initialized early, before the GC traverses us */
    type->tp_flags = spec->flags | Py_TPFLAGS_HEAPTYPE;
    res->ht_name = PyUnicode_FromString(s);
    if (!res->ht_name)
        goto fail;
    res->ht_qualname = res->ht_name;
    Py_INCREF(res->ht_qualname);
    type->tp_name = spec->name;

    Py_XINCREF(module);
    res->ht_module = module;

    /* Adjust for empty tuple bases */
    if (!bases) {
        base = &PyBaseObject_Type;
        /* See whether Py_tp_base(s) was specified */
        for (slot = spec->slots; slot->slot; slot++) {
            if (slot->slot == Py_tp_base)
                base = slot->pfunc;
            else if (slot->slot == Py_tp_bases) {
                bases = slot->pfunc;
            }
        }
        if (!bases) {
            bases = PyTuple_Pack(1, base);
            if (!bases)
                goto fail;
        }
        else if (!PyTuple_Check(bases)) {
            PyErr_SetString(PyExc_SystemError, "Py_tp_bases is not a tuple");
            goto fail;
        }
        else {
            Py_INCREF(bases);
        }
    }
    else if (!PyTuple_Check(bases)) {
        bases = PyTuple_Pack(1, bases);
        if (!bases)
            goto fail;
    }
    else {
        Py_INCREF(bases);
    }

    /* Calculate best base, and check that all bases are type objects */
    base = best_base(bases);
    if (base == NULL) {
        Py_DECREF(bases);
        goto fail;
    }
    if (!_PyType_HasFeature(base, Py_TPFLAGS_BASETYPE)) {
        PyErr_Format(PyExc_TypeError,
                     "type '%.100s' is not an acceptable base type",
                     base->tp_name);
        Py_DECREF(bases);
        goto fail;
    }

    /* Initialize essential fields */
    type->tp_as_async = &res->as_async;
    type->tp_as_number = &res->as_number;
    type->tp_as_sequence = &res->as_sequence;
    type->tp_as_mapping = &res->as_mapping;
    type->tp_as_buffer = &res->as_buffer;
    /* Set tp_base and tp_bases */
    type->tp_bases = bases;
    Py_INCREF(base);
    type->tp_base = base;

    type->tp_basicsize = spec->basicsize;
    type->tp_itemsize = spec->itemsize;

    for (slot = spec->slots; slot->slot; slot++) {
        if (slot->slot < 0
            || (size_t)slot->slot >= Py_ARRAY_LENGTH(pyslot_offsets)) {
            PyErr_SetString(PyExc_RuntimeError, "invalid slot offset");
            goto fail;
        }
        else if (slot->slot == Py_tp_base || slot->slot == Py_tp_bases) {
            /* Processed above */
            continue;
        }
        else if (slot->slot == Py_tp_doc) {
            /* For the docstring slot, which usually points to a static string
               literal, we need to make a copy */
            if (slot->pfunc == NULL) {
                type->tp_doc = NULL;
                continue;
            }
            size_t len = strlen(slot->pfunc)+1;
            char *tp_doc = PyObject_Malloc(len);
            if (tp_doc == NULL) {
                type->tp_doc = NULL;
                PyErr_NoMemory();
                goto fail;
            }
            memcpy(tp_doc, slot->pfunc, len);
            type->tp_doc = tp_doc;
        }
        else if (slot->slot == Py_tp_members) {
            /* Move the slots to the heap type itself */
            size_t len = Py_TYPE(type)->tp_itemsize * nmembers;
            memcpy(PyHeapType_GET_MEMBERS(res), slot->pfunc, len);
            type->tp_members = PyHeapType_GET_MEMBERS(res);
        }
        else {
            /* Copy other slots directly */
            PySlot_Offset slotoffsets = pyslot_offsets[slot->slot];
            slot_offset = slotoffsets.slot_offset;
            if (slotoffsets.subslot_offset == -1) {
                *(void**)((char*)res_start + slot_offset) = slot->pfunc;
            } else {
                void *parent_slot = *(void**)((char*)res_start + slot_offset);
                subslot_offset = slotoffsets.subslot_offset;
                *(void**)((char*)parent_slot + subslot_offset) = slot->pfunc;
            }
        }
    }
    if (type->tp_dealloc == NULL) {
        /* It's a heap type, so needs the heap types' dealloc.
           subtype_dealloc will call the base type's tp_dealloc, if
           necessary. */
        /* TODO(fa): patched out */
        /* type->tp_dealloc = subtype_dealloc; */
    }

    if (vectorcalloffset) {
        type->tp_vectorcall_offset = vectorcalloffset;
    }

    if (PyType_Ready(type) < 0)
        goto fail;

    if (type->tp_dictoffset) {
        res->ht_cached_keys = _PyDict_NewKeysForClass();
    }

    if (type->tp_doc) {
        PyObject *__doc__ = PyUnicode_FromString(_PyType_DocWithoutSignature(type->tp_name, type->tp_doc));
        if (!__doc__)
            goto fail;
        r = _PyDict_SetItemId(type->tp_dict, &PyId___doc__, __doc__);
        Py_DECREF(__doc__);
        if (r < 0)
            goto fail;
    }

    if (weaklistoffset) {
        type->tp_weaklistoffset = weaklistoffset;
        if (PyDict_DelItemString((PyObject *)type->tp_dict, "__weaklistoffset__") < 0)
            goto fail;
    }
    if (dictoffset) {
        type->tp_dictoffset = dictoffset;
        if (PyDict_DelItemString((PyObject *)type->tp_dict, "__dictoffset__") < 0)
            goto fail;
    }

    /* Set type.__module__ */
    r = _PyDict_ContainsId(type->tp_dict, &PyId___module__);
    if (r < 0) {
        goto fail;
    }
    if (r == 0) {
        s = strrchr(spec->name, '.');
        if (s != NULL) {
            modname = PyUnicode_FromStringAndSize(
                    spec->name, (Py_ssize_t)(s - spec->name));
            if (modname == NULL) {
                goto fail;
            }
            r = _PyDict_SetItemId(type->tp_dict, &PyId___module__, modname);
            Py_DECREF(modname);
            if (r != 0)
                goto fail;
        } else {
            if (PyErr_WarnFormat(PyExc_DeprecationWarning, 1,
                    "builtin type %.200s has no __module__ attribute",
                    spec->name))
                goto fail;
        }
    }

    return (PyObject*)res;

 fail:
    Py_DECREF(res);
    return NULL;
}

PyObject *
PyType_FromSpecWithBases(PyType_Spec *spec, PyObject *bases)
{
    return PyType_FromModuleAndSpec(NULL, spec, bases);
}

// taken from CPython "Objects/typeobject.c"
PyObject *
PyType_FromSpec(PyType_Spec *spec)
{
    return PyType_FromSpecWithBases(spec, NULL);
}

// taken from CPython "Objects/typeobject.c"
void *
PyType_GetSlot(PyTypeObject *type, int slot)
{
    void *parent_slot;
    int slots_len = Py_ARRAY_LENGTH(pyslot_offsets);

    if (slot <= 0 || slot >= slots_len) {
        PyErr_BadInternalCall();
        return NULL;
    }

    parent_slot = *(void**)((char*)type + pyslot_offsets[slot].slot_offset);
    if (parent_slot == NULL) {
        return NULL;
    }
    /* Return slot directly if we have no sub slot. */
    if (pyslot_offsets[slot].subslot_offset == -1) {
        return parent_slot;
    }
    return *(void**)((char*)parent_slot + pyslot_offsets[slot].subslot_offset);
}

typedef PyObject* (*type_lookup_fun_t)(PyTypeObject *type, PyObject *name);
UPCALL_TYPED_ID(PyType_Lookup, type_lookup_fun_t);
PyObject * _PyType_Lookup(PyTypeObject *type, PyObject *name) {
    return _jls_PyType_Lookup(native_type_to_java(type), native_to_java(name));
}

// taken from CPython
const char *
_PyType_Name(PyTypeObject *type)
{
    assert(type->tp_name != NULL);
    const char *s = strrchr(type->tp_name, '.');
    if (s == NULL) {
        s = type->tp_name;
    }
    else {
        s++;
    }
    return s;
}

// taken from CPython
PyObject *
_PyType_GetModuleByDef(PyTypeObject *type, struct PyModuleDef *def)
{
    assert(PyType_Check(type));

    PyObject *mro = type->tp_mro;
    // The type must be ready
    assert(mro != NULL);
    assert(PyTuple_Check(mro));
    // mro_invoke() ensures that the type MRO cannot be empty, so we don't have
    // to check i < PyTuple_GET_SIZE(mro) at the first loop iteration.
    assert(PyTuple_GET_SIZE(mro) >= 1);

    Py_ssize_t n = PyTuple_GET_SIZE(mro);
    for (Py_ssize_t i = 0; i < n; i++) {
        PyObject *super = PyTuple_GET_ITEM(mro, i);
        if(!_PyType_HasFeature((PyTypeObject *)super, Py_TPFLAGS_HEAPTYPE)) {
            // Static types in the MRO need to be skipped
            continue;
        }

        PyHeapTypeObject *ht = (PyHeapTypeObject*)super;
        PyObject *module = ht->ht_module;
        if (module && _PyModule_GetDef(module) == def) {
            return module;
        }
    }

    PyErr_Format(
        PyExc_TypeError,
        "_PyType_GetModuleByDef: No superclass of '%s' has the given module",
        type->tp_name);
    return NULL;
}

// Taken from CPython
PyObject *
PyType_GetModule(PyTypeObject *type)
{
    assert(PyType_Check(type));
    if (!_PyType_HasFeature(type, Py_TPFLAGS_HEAPTYPE)) {
        PyErr_Format(
            PyExc_TypeError,
            "PyType_GetModule: Type '%s' is not a heap type",
            type->tp_name);
        return NULL;
    }

    PyHeapTypeObject* et = (PyHeapTypeObject*)type;
    if (!et->ht_module) {
        PyErr_Format(
            PyExc_TypeError,
            "PyType_GetModule: Type '%s' has no associated module",
            type->tp_name);
        return NULL;
    }
    return et->ht_module;

}

// Taken from CPython
void *
PyType_GetModuleState(PyTypeObject *type)
{
    PyObject *m = PyType_GetModule(type);
    if (m == NULL) {
        return NULL;
    }
    return _PyModule_GetState(m);
}
