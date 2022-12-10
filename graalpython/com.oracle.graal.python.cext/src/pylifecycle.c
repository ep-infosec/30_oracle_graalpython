/*
 * Copyright (c) 2019, 2022, Oracle and/or its affiliates. All rights reserved.
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

UPCALL_ID(PyTruffle_FatalErrorFunc);
void _Py_NO_RETURN  _Py_FatalErrorFunc(const char *func, const char *msg) {
	UPCALL_CEXT_VOID(_jls_PyTruffle_FatalErrorFunc, func != NULL? polyglot_from_string(func, SRC_CS): NULL, polyglot_from_string(msg, SRC_CS), -1);
	/* If the above upcall returns, then we just fall through to the 'abort' call. */
	abort();
}

PyOS_sighandler_t PyOS_setsig(int sig, PyOS_sighandler_t handler) {
	PyErr_SetString(PyExc_SystemError, "'PyOS_setsig' not implemented");
	return NULL;
}

int PyOS_InterruptOccurred(void) {
	PyErr_SetString(PyExc_SystemError, "'PyOS_InterruptOccurred' not implemented");
	return -1;
}

typedef void (*py_atexit_fun_t)(void (*func)(void));
UPCALL_TYPED_ID(Py_AtExit, py_atexit_fun_t);
int Py_AtExit(void (*func)(void)) {
    _jls_Py_AtExit(func);
	return 0;
}
