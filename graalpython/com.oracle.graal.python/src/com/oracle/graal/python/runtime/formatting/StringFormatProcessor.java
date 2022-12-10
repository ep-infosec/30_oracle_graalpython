/*
 * Copyright (c) 2017, 2022, Oracle and/or its affiliates.
 * Copyright (c) -2016 Jython Developers
 *
 * Licensed under PYTHON SOFTWARE FOUNDATION LICENSE VERSION 2
 */
package com.oracle.graal.python.runtime.formatting;

import static com.oracle.graal.python.nodes.truffle.TruffleStringMigrationHelpers.isJavaString;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.TypeError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.ValueError;
import static com.oracle.graal.python.util.PythonUtils.TS_ENCODING;
import static com.oracle.graal.python.util.PythonUtils.toTruffleStringUncached;

import com.oracle.graal.python.builtins.Python3Core;
import com.oracle.graal.python.builtins.objects.str.PString;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.builtins.objects.tuple.TupleBuiltins;
import com.oracle.graal.python.lib.PyMappingCheckNode;
import com.oracle.graal.python.lib.PyObjectAsciiNode;
import com.oracle.graal.python.lib.PyObjectGetItem;
import com.oracle.graal.python.lib.PyObjectReprAsTruffleStringNode;
import com.oracle.graal.python.lib.PyObjectStrAsTruffleStringNode;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.runtime.formatting.InternalFormat.Spec;
import com.oracle.truffle.api.strings.TruffleString;

public final class StringFormatProcessor extends FormatProcessor<String> {
    private final String formatText;

    public StringFormatProcessor(Python3Core core, PRaiseNode raiseNode, PyObjectGetItem getItemNode, TupleBuiltins.GetItemNode getTupleItemNode, String format) {
        super(core, raiseNode, getItemNode, getTupleItemNode, new FormattingBuffer.StringFormattingBuffer(format.length() + 100));
        index = 0;
        this.formatText = format;
    }

    @Override
    protected String getFormatType() {
        return "string";
    }

    @Override
    public char pop() {
        try {
            return formatText.charAt(index++);
        } catch (StringIndexOutOfBoundsException e) {
            throw raiseNode.raise(ValueError, ErrorMessages.INCOMPLETE_FORMAT);
        }
    }

    @Override
    boolean hasNext() {
        return index < formatText.length();
    }

    @Override
    int parseNumber(int start, int end) {
        return Integer.parseInt(formatText.substring(start, end));
    }

    @Override
    Object parseMappingKey(int start, int end) {
        return toTruffleStringUncached(formatText.substring(start, end));
    }

    @Override
    protected boolean isMapping(Object obj) {
        // unicodeobject.c PyUnicode_Format()
        return !(obj instanceof PTuple || obj instanceof PString || obj instanceof TruffleString || isJavaString(obj)) && PyMappingCheckNode.getUncached().execute(obj);
    }

    private static boolean isOneCharacter(String str) {
        return str.length() == 1 || (str.length() == 2 && str.codePointCount(0, 2) == 1);
    }

    @Override
    protected InternalFormat.Formatter handleSingleCharacterFormat(Spec spec) {
        InternalFormat.Formatter f;
        TextFormatter ft;
        Object arg = getArg();
        if (arg instanceof PString) {
            arg = ((PString) arg).getValueUncached();
        }
        if (arg instanceof TruffleString && ((TruffleString) arg).codePointLengthUncached(TS_ENCODING) == 1) {
            f = ft = setupFormat(new TextFormatter(raiseNode, buffer, spec));
            ft.format(((TruffleString) arg).toJavaStringUncached());
        } else if (isJavaString(arg) && isOneCharacter((String) arg)) {
            f = ft = setupFormat(new TextFormatter(raiseNode, buffer, spec));
            ft.format((String) arg);
        } else {
            f = formatInteger(asNumber(arg, spec.type), spec);
            if (f == null) {
                throw raiseNode.raise(TypeError, ErrorMessages.REQUIRES_INT_OR_CHAR, spec.type);
            }
        }
        return f;
    }

    @Override
    protected InternalFormat.Formatter handleRemainingFormats(InternalFormat.Spec spec) {
        Object arg = getArg();
        TruffleString result;
        switch (spec.type) {
            case 'a': // repr as ascii
                result = PyObjectAsciiNode.getUncached().execute(null, arg);
                break;
            case 's': // String: converts any object using __str__(), __unicode__() ...
                result = PyObjectStrAsTruffleStringNode.getUncached().execute(null, arg);
                break;
            case 'r': // ... or repr().
                result = PyObjectReprAsTruffleStringNode.getUncached().execute(null, arg);
                break;
            default:
                return null;
        }
        TextFormatter ft = new TextFormatter(raiseNode, buffer, spec);
        ft.format(result.toJavaStringUncached());
        return ft;
    }
}
