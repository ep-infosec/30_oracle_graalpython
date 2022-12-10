# Copyright (c) 2018, 2021, Oracle and/or its affiliates. All rights reserved.
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


def _float_compare(x, y):

    def isNan(x):
        return isinstance(x, float) and x != x

    if (isinstance(x, BaseException) and isinstance(y, BaseException)):
        return type(x) == type(y)
    else:
        # either equal or both are NaN
        return x == y or isNan(x) and isNan(y)


def _reference_asdouble(args):
    n = args[0]
    if isinstance(n, float):
        return n
    try:
        return float(n)
    except:
        if sys.version_info.minor >= 6:
            raise SystemError
        else:
            return -1.0


class DummyNonFloat():
    pass


class DummyFloatable():

    def __float__(self):
        return 3.14159


class DummyFloatSubclass(float):

    def __float__(self):
        return 2.71828


class TestPyFloat(CPyExtTestCase):

    def compile_module(self, name):
        type(self).mro()[1].__dict__["test_%s" % name].create_module(name)
        super(TestPyFloat, self).compile_module(name)

    test_PyFloat_AsDouble = CPyExtFunctionOutVars(
        lambda args: True,
        lambda: (
            (float(0.0), 0.0),
            (float(-1.0), -1.0),
            (float(0xffffffffffffffffffffffff), 0xffffffffffffffffffffffff),
            (float('nan'), float('nan')),
            (DummyFloatable(), 3.14159),
            (DummyFloatSubclass(), 0.0),
            (DummyNonFloat(), -1.0),
        ),
        code='''int wrap_PyFloat_AsDouble(PyObject* obj, double expected) {
            double res = PyFloat_AsDouble(obj);
            PyErr_Clear();
            if (res == expected || (res != res && expected != expected)) {
                return 1;
            } else {
                if (expected != -1.0 && PyErr_Occurred()) {
                    PyErr_Print();
                } else {
                    fprintf(stderr, "expected: %lf\\nactual: %lf\\n", expected, res);
                    fflush(stderr);
                }
                return 0;
            }
        }''',
        resultspec="i",
        argspec='Od',
        arguments=["PyObject* obj", "double expected"],
        resulttype="int",
        callfunction="wrap_PyFloat_AsDouble",
    )

    test_PyFloat_FromDouble = CPyExtFunction(
        lambda args: float(args[0]),
        lambda: (
            (0.0,),
            (-1.0,),
            (-11.123456789123456789,),
            (float('nan'),)
        ),
        resultspec="O",
        argspec='d',
        arguments=["double d"],
        cmpfunc=_float_compare
    )

    test_PyFloat_Check = CPyExtFunction(
        lambda args: isinstance(args[0], float),
        lambda: (
            (0.0,),
            (1.0,),
            (float('nan'),),
            (float(),),
            (DummyNonFloat(),),
            (DummyFloatable(),),
            (DummyFloatSubclass(),),
            (1,),
            (True,),
        ),
        resultspec="i",
        argspec='O',
        arguments=["PyObject* o"],
        cmpfunc=unhandled_error_compare
    )

    test_PyFloat_CheckExact = CPyExtFunction(
        lambda args: type(args[0]) is float,
        lambda: (
            (0.0,),
            (1.0,),
            (float('nan'),),
            (float(),),
            (DummyNonFloat(),),
            (DummyFloatable(),),
            (DummyFloatSubclass(),),
            (1,),
            (True,),
        ),
        resultspec="i",
        argspec='O',
        arguments=["PyObject* o"],
        cmpfunc=unhandled_error_compare
    )
