# Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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

import tempfile            

tmp_write_file = None

def _reference_write_object(args):
    if args[1] is None:
        if sys.version_info.minor >= 6:
            raise SystemError
        else:
            raise TypeError
    try:    
        args[1].write(args[0])
    except Exception as e:
        if sys.version_info.minor >= 6:
            raise SystemError
        else:
            raise e
    return 0

class TestPyFile(CPyExtTestCase):
    
    def setUp(self):
        tmp_file_path = tempfile.mktemp(prefix="cext-file-test")
        global tmp_write_file
        open(tmp_file_path, "w")
            
    def tearDown(self):
        if tmp_write_file:
            tmp_write_file.close()
        
    def compile_module(self, name):
        type(self).mro()[1].__dict__["test_%s" % name].create_module(name)
        super(TestPyFile, self).compile_module(name)

    test_PyFile_WriteObject = CPyExtFunction(
        _reference_write_object,
        lambda: (
            ("hello", None, 1),             
            ("hello", "nofile", 1),
            ("hello", tmp_write_file, 1),
        ),
        resultspec="i",
        argspec='OOi',
        arguments=["PyObject* o", "PyObject* f", "int flags"],
        cmpfunc=unhandled_error_compare
    )
