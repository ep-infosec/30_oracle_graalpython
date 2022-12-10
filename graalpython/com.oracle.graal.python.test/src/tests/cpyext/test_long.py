# Copyright (c) 2018, 2022, Oracle and/or its affiliates. All rights reserved.
# DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
#
# The Universal Permissive License (UPL), Version 1.0
#
# Subject to the condition set forth below, permission is hereby granted to any
# person obtaining a copy of this software, associated documentation and/or
# data (collectively the "Software"), free of charge and under any and all
# copyright rights in the Software, and any and all patent rights owned or
# freely licensable by each licensor hereunder covering either (i) the
# unmodified Software as contributed to or provided by such licensor, or (ii)
# the Larger Works (as defined below), to deal in both
#
# (a) the Software, and
#
# (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
# one is included with the Software each a "Larger Work" to which the Software
# is contributed by such licensors),
#
# without restriction, including without limitation the rights to copy, create
# derivative works of, display, perform, and distribute the Software and make,
# use, sell, offer for sale, import, export, have made, and have sold the
# Software and the Larger Work(s), and to sublicense the foregoing rights on
# either these or other terms.
#
# This license is subject to the following condition:
#
# The above copyright notice and either this complete permission notice or at a
# minimum a reference to the UPL must be included in all copies or substantial
# portions of the Software.
#
# THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
# IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
# FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
# AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
# LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
# OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
# SOFTWARE.

import sys
from . import CPyExtTestCase, CPyExtFunction, CPyExtFunctionOutVars, unhandled_error_compare, GRAALPYTHON
__dir__ = __file__.rpartition("/")[0]

def raise_Py6_SystemError():
    if sys.version_info.minor >= 6:
        raise SystemError
    else:
        return -1
    
def _reference_aslong(args):
    # We cannot be sure if we are on 32-bit or 64-bit architecture. So, assume the smaller one.
    n = int(args[0])
    if n > 0x7fffffff:
        if sys.version_info.minor >= 6:
            raise SystemError
        else:
            return -1
    return n


def _reference_as_unsigned_long(args):
    # We cannot be sure if we are on 32-bit or 64-bit architecture. So, assume the smaller one.
    n = args[0]
    if n > 0xffffffff or n < 0:
        if sys.version_info.minor >= 6:
            exc = SystemError()
            exc.__cause__ = OverflowError()
            raise exc
        else:
            return -1
    return int(n)


def _reference_aslong_overflow(args):
    # We cannot be sure if we are on 32-bit or 64-bit architecture. So, assume the smaller one.
    n = args[0]
    if n > 0x7fffffff:
        if sys.version_info.minor >= 6:
            raise SystemError
        else:
            return (-1, 1)
    return (int(n), 0)


def _reference_fromvoidptr(args):
    n = args[0]
    if n < 0:
        return ((~abs(n)) & 0xffffffffffffffff) + 1
    return n


def _reference_fromlong(args):
    n = args[0]
    return n

def _reference_sign(args):
    n = args[0]
    if n==0:
        return 0
    elif n < 0:
        return -1
    else:
        return 1

class DummyNonInt():
    pass


class DummyIndexable:

    def __index__(self):
        return 0xBEEF


class TestPyLong(CPyExtTestCase):

    def compile_module(self, name):
        type(self).mro()[1].__dict__["test_%s" % name].create_module(name)
        super(TestPyLong, self).compile_module(name)

    test_PyLong_AsLong = CPyExtFunction(
        lambda args: True,
        lambda: (
            (0, 0),
            (-1, -1),
            (0x7fffffff, 0x7fffffff),
            (0xffffffffffffffffffffffffffffffff, -1),
            (DummyNonInt(), -1),
            (DummyIndexable(), 0xBEEF if sys.version_info >= (3, 8, 0) else 0xDEAD),
        ),
        code='''int wrap_PyLong_AsLong(PyObject* obj, long expected) {
            long res = PyLong_AsLong(obj);
            PyErr_Clear();
            if (res == expected) {
                return 1;
            } else {
                if (expected != -1 && PyErr_Occurred()) {
                    PyErr_Print();
                } else {
                    fprintf(stderr, "expected: %ld\\nactual: %ld\\n", expected, res);
                    fflush(stderr);
                }
                return 0;
            }
        }''',
        resultspec="l",
        argspec='Ol',
        arguments=["PyObject* obj", "long expected"],
        callfunction="wrap_PyLong_AsLong",
        cmpfunc=unhandled_error_compare
    )

    test_PyLong_AsLongAndOverflow = CPyExtFunctionOutVars(
        _reference_aslong_overflow,
        lambda: (
            (0,),
            (-1,),
            (0x7fffffff,),
        ),
        resultspec="li",
        argspec='O',
        arguments=["PyObject* obj"],
        resulttype="long",
        resultvars=["int overflow"],
        cmpfunc=unhandled_error_compare
    )

    test_PyLong_AsUnsignedLong = CPyExtFunction(
        _reference_as_unsigned_long,
        lambda: (
            (0,),
            (-1,),
            (-2,),
            (True,),
            (False,),
            (0x7fffffff,),
            (0xffffffff,),
            # we could use larger values on 64-bit systems but how should we know?
        ),
        resultspec="k",
        argspec='O',
        arguments=["PyObject* obj"],
        cmpfunc=unhandled_error_compare
    )

    test_PyLong_AsSsize_t = CPyExtFunction(
        lambda args: int(args[0]),
        lambda: (
            (0,),
            (-1,),
            (0x7fffffff,),
        ),
        resultspec="n",
        argspec='O',
        arguments=["PyObject* obj"],
        cmpfunc=unhandled_error_compare
    )

    test_PyLong_FromSsize_t = CPyExtFunction(
        lambda args: int(args[0]),
        lambda: (
            (0,),
            (-1,),
            (1,),
            (0xffffffff,),
        ),
        resultspec="O",
        argspec='n',
        arguments=["Py_ssize_t n"],
        cmpfunc=unhandled_error_compare
    )

    test_PyLong_FromSize_t = CPyExtFunction(
        lambda args: int(args[0]),
        lambda: (
            (0,),
            (1,),
            (0xffffffff,),
        ),
        resultspec="O",
        argspec='n',
        arguments=["size_t n"],
        cmpfunc=unhandled_error_compare
    )

    test_PyLong_FromDouble = CPyExtFunction(
        lambda args: int(args[0]),
        lambda: (
            (0.0,),
            (-1.0,),
            (-11.123456789123456789,),
        ),
        resultspec="O",
        argspec='d',
        arguments=["double d"],
        cmpfunc=unhandled_error_compare
    )

    test_PyLong_FromVoidPtr = CPyExtFunction(
        _reference_fromvoidptr,
        lambda: (
            (0,),
            (-1,),
            (-2,),
            (1,),
            (0xffffffff,),
        ),
        resultspec="O",
        argspec='n',
        arguments=["void* ptr"],
        cmpfunc=unhandled_error_compare
    )

    test_PyLong_FromVoidPtrAllocated = CPyExtFunction(
        lambda args: int,
        lambda: ((None,),),
        code="""PyObject* PyLong_FromVoidPtrAllocated(PyObject* none) {
            void* dummyPtr = malloc(sizeof(size_t));
            return (PyObject*)Py_TYPE(PyLong_FromVoidPtr(dummyPtr));
        }
        """,
        resultspec="O",
        argspec='O',
        arguments=["PyObject* none"],
        cmpfunc=unhandled_error_compare
    )

    test_PyLong_AsVoidPtrAllocated = CPyExtFunction(
        lambda args: True,
        lambda: ((None,),),
        code="""PyObject* PyLong_AsVoidPtrAllocated(PyObject* none) {
            void* dummyPtr = malloc(sizeof(size_t));
            PyObject* obj = PyLong_FromVoidPtr(dummyPtr);
            void* unwrappedPtr = PyLong_AsVoidPtr(obj);
            PyObject* result = unwrappedPtr == dummyPtr ? Py_True : Py_False;
            free(dummyPtr);
            return result;
        }
        """,
        resultspec="O",
        argspec='O',
        arguments=["PyObject* none"],
        cmpfunc=unhandled_error_compare
    )

    # We get a pattern like this in Cython generated code
    test_PyLong_FromAndToVoidPtrAllocated = CPyExtFunction(
        lambda args: True,
        lambda: ((None,),),
        code="""PyObject* PyLong_FromAndToVoidPtrAllocated(PyObject* none) {
            unsigned long l = 0;
            void* unwrappedPtr;
            PyObject* result;
            void* dummyPtr = malloc(sizeof(size_t));
            PyObject* obj = PyLong_FromVoidPtr(dummyPtr);
            int r = PyObject_RichCompareBool(obj, Py_False, Py_LT);
            if (r < 0) {
                return Py_None;
            }
            l = PyLong_AsUnsignedLong(obj);
            unwrappedPtr = (void*)l;
            result = unwrappedPtr == dummyPtr ? Py_True : Py_False;
            free(dummyPtr);
            return result;
        }
        """,
        resultspec="O",
        argspec='O',
        arguments=["PyObject* none"],
        cmpfunc=unhandled_error_compare
    )

    test_PyLong_Check = CPyExtFunction(
        lambda args: isinstance(args[0], int),
        lambda: (
            (0,),
            (-1,),
            (0xffffffff,),
            (0xfffffffffffffffffffffff,),
            ("hello",),
            (DummyNonInt(),),
        ),
        resultspec="i",
        argspec='O',
        arguments=["PyObject* o"],
        cmpfunc=unhandled_error_compare
    )

    test_PyLong_CheckExact = CPyExtFunction(
        lambda args: type(args[0]) is int,
        lambda: (
            (0,),
            (-1,),
            (0xffffffff,),
            (0xfffffffffffffffffffffff,),
            ("hello",),
            (DummyNonInt(),),
        ),
        resultspec="i",
        argspec='O',
        arguments=["PyObject* o"],
        cmpfunc=unhandled_error_compare
    )

    test_PyLong_FromString = CPyExtFunction(
        lambda args: int(args[0], args[1]),
        lambda: (
            ("  12 ", 10),
            ("12", 0),
        ),
        code='''PyObject* wrap_PyLong_FromString(const char* str, int base) {
            char* pend;
            return PyLong_FromString(str, &pend, base);
        }''',
        callfunction="wrap_PyLong_FromString",
        resultspec="O",
        argspec="si",
        arguments=["char* string", "int base"],
    )

    test_PyLong_AsByteArray = CPyExtFunction(
        lambda args: args[4],
        lambda: (
            (0, 8, False, True, b'\x00\x00\x00\x00\x00\x00\x00\x00'),
            (4294967299, 8, False, True, b'\x00\x00\x00\x01\x00\x00\x00\x03'),
            (1234, 8, False, True, b'\x00\x00\x00\x00\x00\x00\x04\xd2'),
            (0xdeadbeefdead, 8, False, True, b'\x00\x00\xde\xad\xbe\xef\xde\xad'),
            (0xdeadbeefdead, 8, True, True, b'\xad\xde\xef\xbe\xad\xde\x00\x00'),
            (0xdeadbeefdeadbeefbeefdeadcafebabe, 17, False, True, b'\x00\xde\xad\xbe\xef\xde\xad\xbe\xef\xbe\xef\xde\xad\xca\xfe\xba\xbe'),
        ),
        code='''PyObject* wrap_PyLong_AsByteArray(PyObject* object, Py_ssize_t n, int little_endian, int is_signed, PyObject* unused) {
            unsigned char* buf = (unsigned char *) malloc(n * sizeof(unsigned char));
            PyObject* result;
            
            Py_INCREF(object);
            if (_PyLong_AsByteArray((PyLongObject*) object, buf, n, little_endian, is_signed)) {
                Py_DECREF(object);
                return NULL;
            }
            Py_DECREF(object);
            result = PyBytes_FromStringAndSize((const char *) buf, n);
            free(buf);
            return result;
        }''',
        callfunction="wrap_PyLong_AsByteArray",
        resultspec="O",
        argspec="OniiO",
        arguments=["PyObject* object", "Py_ssize_t n", "int little_endian", "int is_signed", "PyObject* unused"],
    )
    
    test__PyLong_Sign = CPyExtFunction(
        _reference_sign,
        lambda: (
            (0,),
            (-1,),
            (0xffffffff,),
            (0xfffffffffffffffffffffff,),
            (True,),
            (False,),
        ),
        resultspec="i",
        argspec='O',
        arguments=["PyObject* o"],
        cmpfunc=unhandled_error_compare
    )
