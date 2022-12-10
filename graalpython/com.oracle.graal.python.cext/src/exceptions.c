/*
 * Copyright (c) 2018, 2021, Oracle and/or its affiliates. All rights reserved.
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

#include <pyerrors.h>

PyTypeObject _PyExc_BaseException = PY_TRUFFLE_TYPE("BaseException", &PyType_Type, Py_TPFLAGS_DEFAULT | Py_TPFLAGS_HAVE_GC | Py_TPFLAGS_BASETYPE | Py_TPFLAGS_BASE_EXC_SUBCLASS, sizeof(PyBaseExceptionObject));
PyTypeObject _PyExc_StopIteration = PY_TRUFFLE_TYPE("StopIteration", &PyType_Type, Py_TPFLAGS_DEFAULT | Py_TPFLAGS_HAVE_GC | Py_TPFLAGS_BASETYPE | Py_TPFLAGS_BASE_EXC_SUBCLASS, sizeof(PyStopIterationObject));

#define PY_EXCEPTION(__EXC_NAME__) (UPCALL_CEXT_O(polyglot_from_string("PyTruffle_Type", SRC_CS), polyglot_from_string(__EXC_NAME__, SRC_CS)))

PyObject * PyExc_BaseException = NULL;
PyObject * PyExc_Exception = NULL;
PyObject * PyExc_StopAsyncIteration = NULL;
PyObject * PyExc_StopIteration = NULL;
PyObject * PyExc_GeneratorExit = NULL;
PyObject * PyExc_ArithmeticError = NULL;
PyObject * PyExc_LookupError = NULL;
PyObject * PyExc_AssertionError = NULL;
PyObject * PyExc_AttributeError = NULL;
PyObject * PyExc_BufferError = NULL;
PyObject * PyExc_EOFError = NULL;
PyObject * PyExc_FloatingPointError = NULL;
PyObject * PyExc_OSError = NULL;
PyObject * PyExc_ImportError = NULL;
PyObject * PyExc_ModuleNotFoundError = NULL;
PyObject * PyExc_IndexError = NULL;
PyObject * PyExc_KeyError = NULL;
PyObject * PyExc_KeyboardInterrupt = NULL;
PyObject * PyExc_MemoryError = NULL;
PyObject * PyExc_NameError = NULL;
PyObject * PyExc_OverflowError = NULL;
PyObject * PyExc_RuntimeError = NULL;
PyObject * PyExc_RecursionError = NULL;
PyObject * PyExc_NotImplementedError = NULL;
PyObject * PyExc_SyntaxError = NULL;
PyObject * PyExc_IndentationError = NULL;
PyObject * PyExc_TabError = NULL;
PyObject * PyExc_ReferenceError = NULL;
PyObject * PyExc_SystemError = NULL;
PyObject * PyExc_SystemExit = NULL;
PyObject * PyExc_TypeError = NULL;
PyObject * PyExc_UnboundLocalError = NULL;
PyObject * PyExc_UnicodeError = NULL;
PyObject * PyExc_UnicodeEncodeError = NULL;
PyObject * PyExc_UnicodeDecodeError = NULL;
PyObject * PyExc_UnicodeTranslateError = NULL;
PyObject * PyExc_ValueError = NULL;
PyObject * PyExc_ZeroDivisionError = NULL;
PyObject * PyExc_BlockingIOError = NULL;
PyObject * PyExc_BrokenPipeError = NULL;
PyObject * PyExc_ChildProcessError = NULL;
PyObject * PyExc_ConnectionError = NULL;
PyObject * PyExc_ConnectionAbortedError = NULL;
PyObject * PyExc_ConnectionRefusedError = NULL;
PyObject * PyExc_ConnectionResetError = NULL;
PyObject * PyExc_FileExistsError = NULL;
PyObject * PyExc_FileNotFoundError = NULL;
PyObject * PyExc_InterruptedError = NULL;
PyObject * PyExc_IsADirectoryError = NULL;
PyObject * PyExc_NotADirectoryError = NULL;
PyObject * PyExc_PermissionError = NULL;
PyObject * PyExc_ProcessLookupError = NULL;
PyObject * PyExc_TimeoutError = NULL;

PyObject * PyExc_EnvironmentError = NULL;
PyObject * PyExc_IOError = NULL;

PyObject * PyExc_Warning = NULL;
PyObject * PyExc_UserWarning = NULL;
PyObject * PyExc_DeprecationWarning = NULL;
PyObject * PyExc_PendingDeprecationWarning = NULL;
PyObject * PyExc_SyntaxWarning = NULL;
PyObject * PyExc_RuntimeWarning = NULL;
PyObject * PyExc_FutureWarning = NULL;
PyObject * PyExc_ImportWarning = NULL;
PyObject * PyExc_UnicodeWarning = NULL;
PyObject * PyExc_BytesWarning = NULL;
PyObject * PyExc_ResourceWarning = NULL;

void initialize_exceptions() {
    PyExc_BaseException = PY_EXCEPTION("BaseException");
    PyExc_Exception = PY_EXCEPTION("Exception");
    PyExc_StopAsyncIteration = PY_EXCEPTION("StopAsyncIteration");
    PyExc_StopIteration = PY_EXCEPTION("StopIteration");
    PyExc_GeneratorExit = PY_EXCEPTION("GeneratorExit");
    PyExc_ArithmeticError = PY_EXCEPTION("ArithmeticError");
    PyExc_LookupError = PY_EXCEPTION("LookupError");
    PyExc_AssertionError = PY_EXCEPTION("AssertionError");
    PyExc_AttributeError = PY_EXCEPTION("AttributeError");
    PyExc_BufferError = PY_EXCEPTION("BufferError");
    PyExc_EOFError = PY_EXCEPTION("EOFError");
    PyExc_FloatingPointError = PY_EXCEPTION("FloatingPointError");
    PyExc_OSError = PY_EXCEPTION("OSError");
    PyExc_ImportError = PY_EXCEPTION("ImportError");
    PyExc_ModuleNotFoundError = PY_EXCEPTION("ModuleNotFoundError");
    PyExc_IndexError = PY_EXCEPTION("IndexError");
    PyExc_KeyError = PY_EXCEPTION("KeyError");
    PyExc_KeyboardInterrupt = PY_EXCEPTION("KeyboardInterrupt");
    PyExc_MemoryError = PY_EXCEPTION("MemoryError");
    PyExc_NameError = PY_EXCEPTION("NameError");
    PyExc_OverflowError = PY_EXCEPTION("OverflowError");
    PyExc_RuntimeError = PY_EXCEPTION("RuntimeError");
    PyExc_RecursionError = PY_EXCEPTION("RecursionError");
    PyExc_NotImplementedError = PY_EXCEPTION("NotImplementedError");
    PyExc_SyntaxError = PY_EXCEPTION("SyntaxError");
    PyExc_IndentationError = PY_EXCEPTION("IndentationError");
    PyExc_TabError = PY_EXCEPTION("TabError");
    PyExc_ReferenceError = PY_EXCEPTION("ReferenceError");
    PyExc_SystemError = PY_EXCEPTION("SystemError");
    PyExc_SystemExit = PY_EXCEPTION("SystemExit");
    PyExc_TypeError = PY_EXCEPTION("TypeError");
    PyExc_UnboundLocalError = PY_EXCEPTION("UnboundLocalError");
    PyExc_UnicodeError = PY_EXCEPTION("UnicodeError");
    PyExc_UnicodeEncodeError = PY_EXCEPTION("UnicodeEncodeError");
    PyExc_UnicodeDecodeError = PY_EXCEPTION("UnicodeDecodeError");
    PyExc_UnicodeTranslateError = PY_EXCEPTION("UnicodeTranslateError");
    PyExc_ValueError = PY_EXCEPTION("ValueError");
    PyExc_ZeroDivisionError = PY_EXCEPTION("ZeroDivisionError");
    PyExc_BlockingIOError = PY_EXCEPTION("BlockingIOError");
    PyExc_BrokenPipeError = PY_EXCEPTION("BrokenPipeError");
    PyExc_ChildProcessError = PY_EXCEPTION("ChildProcessError");
    PyExc_ConnectionError = PY_EXCEPTION("ConnectionError");
    PyExc_ConnectionAbortedError = PY_EXCEPTION("ConnectionAbortedError");
    PyExc_ConnectionRefusedError = PY_EXCEPTION("ConnectionRefusedError");
    PyExc_ConnectionResetError = PY_EXCEPTION("ConnectionResetError");
    PyExc_FileExistsError = PY_EXCEPTION("FileExistsError");
    PyExc_FileNotFoundError = PY_EXCEPTION("FileNotFoundError");
    PyExc_InterruptedError = PY_EXCEPTION("InterruptedError");
    PyExc_IsADirectoryError = PY_EXCEPTION("IsADirectoryError");
    PyExc_NotADirectoryError = PY_EXCEPTION("NotADirectoryError");
    PyExc_PermissionError = PY_EXCEPTION("PermissionError");
    PyExc_ProcessLookupError = PY_EXCEPTION("ProcessLookupError");
    PyExc_TimeoutError = PY_EXCEPTION("TimeoutError");

    PyExc_EnvironmentError = PY_EXCEPTION("EnvironmentError");
    PyExc_IOError = PY_EXCEPTION("IOError");

    PyExc_Warning = PY_EXCEPTION("Warning");
    PyExc_UserWarning = PY_EXCEPTION("UserWarning");
    PyExc_DeprecationWarning = PY_EXCEPTION("DeprecationWarning");
    PyExc_PendingDeprecationWarning = PY_EXCEPTION("PendingDeprecationWarning");
    PyExc_SyntaxWarning = PY_EXCEPTION("SyntaxWarning");
    PyExc_RuntimeWarning = PY_EXCEPTION("RuntimeWarning");
    PyExc_FutureWarning = PY_EXCEPTION("FutureWarning");
    PyExc_ImportWarning = PY_EXCEPTION("ImportWarning");
    PyExc_UnicodeWarning = PY_EXCEPTION("UnicodeWarning");
    PyExc_BytesWarning = PY_EXCEPTION("BytesWarning");
    PyExc_ResourceWarning = PY_EXCEPTION("ResourceWarning");
}


int PyException_SetTraceback(PyObject *self, PyObject *tb) {
    PyObject* result = UPCALL_O(native_to_java(self), polyglot_from_string("with_traceback", SRC_CS), native_to_java(tb));
    if (result == NULL) {
        return -1;
    } else {
        return 0;
    }
}

UPCALL_ID(PyException_SetCause);
void PyException_SetCause(PyObject *self, PyObject *cause) {
	UPCALL_CEXT_VOID(_jls_PyException_SetCause, native_to_java(self), native_to_java(cause));
}

UPCALL_ID(PyException_GetContext);
PyObject * PyException_GetContext(PyObject *self) {
    return UPCALL_CEXT_O(_jls_PyException_GetContext, native_to_java(self));
}

UPCALL_ID(PyException_SetContext);
void PyException_SetContext(PyObject *self, PyObject *context) {
    UPCALL_CEXT_VOID(_jls_PyException_SetContext, native_to_java(self), native_to_java(context));
}
