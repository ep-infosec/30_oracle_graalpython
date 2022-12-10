/*
 * Copyright (c) 2021, 2022, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.builtins.objects.function;

import static com.oracle.graal.python.builtins.objects.function.BuiltinMethodDescriptor.get;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___GETATTRIBUTE__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___ITER__;

import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.objects.dict.DictBuiltinsFactory;
import com.oracle.graal.python.builtins.objects.module.ModuleBuiltinsFactory;
import com.oracle.graal.python.builtins.objects.object.ObjectBuiltinsFactory;
import com.oracle.graal.python.builtins.objects.type.TypeBuiltinsFactory;

/**
 * Enum-like class with some useful well known descriptors. Because of initialization order issues,
 * these constants cannot be places in {@link BuiltinMethodDescriptor} class.
 */
public abstract class BuiltinMethodDescriptors {

    public static final BuiltinMethodDescriptor OBJ_GET_ATTRIBUTE = get(J___GETATTRIBUTE__, ObjectBuiltinsFactory.GetAttributeNodeFactory.getInstance(), PythonBuiltinClassType.PythonObject);
    public static final BuiltinMethodDescriptor MODULE_GET_ATTRIBUTE = get(J___GETATTRIBUTE__, ModuleBuiltinsFactory.ModuleGetattritbuteNodeFactory.getInstance(), PythonBuiltinClassType.PythonModule);
    public static final BuiltinMethodDescriptor TYPE_GET_ATTRIBUTE = get(J___GETATTRIBUTE__, TypeBuiltinsFactory.GetattributeNodeFactory.getInstance(), PythonBuiltinClassType.PythonClass);

    public static final BuiltinMethodDescriptor DICT_ITER = get(J___ITER__, DictBuiltinsFactory.IterNodeFactory.getInstance(), PythonBuiltinClassType.PDict);

    private BuiltinMethodDescriptors() {
    }
}
