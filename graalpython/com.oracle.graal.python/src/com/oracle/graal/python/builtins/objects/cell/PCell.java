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
package com.oracle.graal.python.builtins.objects.cell;

import com.oracle.graal.python.builtins.objects.PythonAbstractObject;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;

public final class PCell extends PythonAbstractObject {
    private final Assumption effectivelyFinal;
    private Object ref;

    public PCell(Assumption effectivelyFinalAssumption) {
        this.effectivelyFinal = effectivelyFinalAssumption;
    }

    public static PCell[] toCellArray(Object[] closure) {
        PCell[] cells = new PCell[closure.length];
        PythonUtils.arraycopy(closure, 0, cells, 0, closure.length);
        return cells;
    }

    public Object getRef() {
        return ref;
    }

    public void clearRef(Assumption assumption) {
        setRef(null, assumption);
    }

    public void clearRef() {
        setRef(null);
    }

    @TruffleBoundary
    private static void invalidateAssumption(Assumption assumption) {
        assumption.invalidate();
    }

    public void setRef(Object ref) {
        if (this.ref != null) {
            invalidateAssumption(effectivelyFinal);
        }
        this.ref = ref;
    }

    /**
     * Use this to pass in the effectivelyFinal assumption from a node that made it constant.
     */
    public void setRef(Object ref, Assumption constantAssumption) {
        assert constantAssumption == effectivelyFinal;
        if (constantAssumption.isValid()) {
            if (this.ref != null) {
                invalidateAssumption(constantAssumption);
            }
        }
        this.ref = ref;
    }

    public Assumption isEffectivelyFinalAssumption() {
        return effectivelyFinal;
    }

    @Override
    public String toString() {
        CompilerAsserts.neverPartOfCompilation();
        if (ref == null) {
            return String.format("<cell at %s: empty>", hashCode());
        }
        return String.format("<cell at %s: %s object at %s>", hashCode(), ref.getClass().getSimpleName(), ref.hashCode());
    }

    @Override
    public int compareTo(Object o) {
        CompilerDirectives.transferToInterpreterAndInvalidate();
        throw new UnsupportedOperationException();
    }
}