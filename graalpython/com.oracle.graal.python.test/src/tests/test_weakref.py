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

MAX_WAIT_COUNT = 500

import sys

# TODO: re-enable test_weakref_finalizer once the transient issue associated with it is solved (see GR-27104).
def _weakref_finalizer():
    import gc, weakref
    class A(): pass
    for i in range(2):
        w = weakref.ref(A(), cleanup)
    i = 0
    while not cleaned_up and i < MAX_WAIT_COUNT:
        gc.collect()
        i += 1
    assert not w()
    assert cleaned_up


cleaned_up = False
def cleanup(ref):
    global cleaned_up
    caller_code = sys._getframe(1).f_code
    assert caller_code == _weakref_finalizer.__code__, "expected: '%s' but was '%s'" % (_weakref_finalizer.__code__, caller_code)
    cleaned_up = True


def test_weakref_hash():
    from collections import UserString as ustr
    from weakref import ref
    import gc

    o1 = ustr('a')
    o2 = ustr('a')
    o3 = ustr('b')
    r1 = ref(o1)
    r2 = ref(o2)
    r3 = ref(o3)
    r1_hash = hash(r1)
    assert hash(o1) == hash(r1)
    assert hash(o2) == hash(r2)
    assert hash(r1) == hash(r2)

    # we need a function that calls 'hash' otherwise 'hash' is inlined and will specialize differently
    def do_hash(item):
        return hash(item)

    o1 = None
    o2 = None
    o3 = None

    # let them die

    # try hard to collect the two objects but avoid infinite loop
    i = 0
    while not (r1() is None and r3() is None) and i < 10000:
        gc.collect()
        i += 1

    # We still cannot guarantee that the objects were collected; so avoid introducing transient errors and just do
    # not test if they weren't collected.
    if r1() is None and r3() is None:
        try:
            # it's important that we've never computed the has for r3 before
            do_hash(r3)
        except TypeError as e:
            pass
        else:
            assert False, "could compute hash for r3 but should have failed"

        assert r1_hash == do_hash(r1)
