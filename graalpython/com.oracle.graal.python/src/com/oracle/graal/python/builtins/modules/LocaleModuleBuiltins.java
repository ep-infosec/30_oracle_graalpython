/*
 * Copyright (c) 2017, 2022, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.builtins.modules;

import static com.oracle.graal.python.util.PythonUtils.TS_ENCODING;
import static com.oracle.graal.python.util.PythonUtils.toTruffleStringUncached;

import java.nio.charset.Charset;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.NumberFormat;
import java.util.Currency;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;

import org.graalvm.nativeimage.ImageInfo;

import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.Python3Core;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.lib.PyLongAsLongNode;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.nodes.util.CannotCastException;
import com.oracle.graal.python.nodes.util.CastToTruffleStringNode;
import com.oracle.graal.python.runtime.exception.PythonErrorType;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.strings.TruffleString;

@CoreFunctions(defineModule = "_locale")
public class LocaleModuleBuiltins extends PythonBuiltins {
    static final int LC_ALL = 6;
    static final int LC_COLLATE = 3;
    static final int LC_CTYPE = 0;
    static final int LC_MESSAGES = 5;
    static final int LC_MONETARY = 4;
    static final int LC_NUMERIC = 1;
    static final int LC_TIME = 2;
    static final int CHAR_MAX = 127;

    @TruffleBoundary
    public static Locale fromPosix(TruffleString tposixLocaleId) {
        // format: [language[_territory][.variant][@modifier]]
        // 2 lower _ 2 UPPER .
        if (tposixLocaleId == null) {
            return null;
        }
        if (tposixLocaleId.isEmpty()) {
            // per Python docs: empty string -> default locale
            return Locale.getDefault();
        }

        String posixLocaleId = tposixLocaleId.toJavaStringUncached();
        String language;
        String country = "";
        String variant = "";

        int len = posixLocaleId.length();

        // get the language
        int posCountrySep = posixLocaleId.indexOf('_');
        if (posCountrySep < 0) {
            language = posixLocaleId;
        } else {
            language = posixLocaleId.substring(0, posCountrySep);

            int posVariantSep = posixLocaleId.indexOf('.');
            if (posVariantSep < 0) {
                country = posixLocaleId.substring(posCountrySep + 1, len);
            } else {
                country = posixLocaleId.substring(posCountrySep + 1, posVariantSep);
                variant = posixLocaleId.substring(posVariantSep + 1, len);
            }
        }

        if (!language.isEmpty() && language.length() != 2) {
            return null;
        }

        if (!country.isEmpty() && country.length() != 2) {
            return null;
        }

        return new Locale(language, country, variant);
    }

    @TruffleBoundary
    public static TruffleString toPosix(Locale locale) {
        if (locale == null) {
            return null;
        }
        StringBuilder builder = new StringBuilder();
        String language = locale.getLanguage();
        if (language.isEmpty()) {
            language = locale.getISO3Language();
        }

        if (!language.isEmpty()) {
            builder.append(language);

            String country = locale.getCountry();
            if (country.isEmpty()) {
                country = locale.getISO3Country();
            }

            if (!country.isEmpty()) {
                builder.append('_');
                builder.append(country.toUpperCase());

                Charset charset = Charset.defaultCharset();
                builder.append('.');
                builder.append(charset.name());
            }
        } else {
            return null;
        }

        return toTruffleStringUncached(builder.toString());
    }

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinNode>> getNodeFactories() {
        return LocaleModuleBuiltinsFactory.getFactories();
    }

    @Override
    public void initialize(Python3Core core) {
        addBuiltinConstant("LC_ALL", 6);
        addBuiltinConstant("LC_COLLATE", 3);
        addBuiltinConstant("LC_CTYPE", 0);
        addBuiltinConstant("LC_MESSAGES", 5);
        addBuiltinConstant("LC_MONETARY", 4);
        addBuiltinConstant("LC_NUMERIC", 1);
        addBuiltinConstant("LC_TIME", 2);
        addBuiltinConstant("CHAR_MAX", 127);

        addBuiltinConstant("Error", PythonBuiltinClassType.ValueError);

        super.initialize(core);
    }

    // _locale.localeconv()
    @Builtin(name = "localeconv")
    @GenerateNodeFactory
    public abstract static class LocaleConvNode extends PythonBuiltinNode {
        @Specialization
        @TruffleBoundary
        public PDict localeconv() {
            LinkedHashMap<String, Object> dict = new LinkedHashMap<>(20);

            // get default locale for the format category
            Locale locale = Locale.getDefault(Locale.Category.FORMAT);
            NumberFormat numberFormat = NumberFormat.getInstance(locale);
            Currency currency = numberFormat.getCurrency();

            DecimalFormatSymbols decimalFormatSymbols;
            int groupSize = -1;
            if (numberFormat instanceof DecimalFormat) {
                DecimalFormat decimalFormat = (DecimalFormat) numberFormat;
                decimalFormatSymbols = decimalFormat.getDecimalFormatSymbols();
                groupSize = decimalFormat.getGroupingSize();
            } else {
                decimalFormatSymbols = new DecimalFormatSymbols(locale);
            }

            // LC_NUMERIC
            dict.put("decimal_point", TruffleString.fromCodePointUncached(decimalFormatSymbols.getDecimalSeparator(), TS_ENCODING));
            dict.put("thousands_sep", TruffleString.fromCodePointUncached(decimalFormatSymbols.getGroupingSeparator(), TS_ENCODING));
            // TODO: set the proper grouping
            if (groupSize != -1) {
                dict.put("grouping", factory().createList(new Object[]{groupSize, 0}));
            } else {
                dict.put("grouping", factory().createList());
            }

            // LC_MONETARY
            dict.put("int_curr_symbol", toTruffleStringUncached(decimalFormatSymbols.getInternationalCurrencySymbol()));
            dict.put("currency_symbol", toTruffleStringUncached(decimalFormatSymbols.getCurrencySymbol()));
            dict.put("mon_decimal_point", TruffleString.fromCodePointUncached(decimalFormatSymbols.getMonetaryDecimalSeparator(), TS_ENCODING));
            dict.put("mon_thousands_sep", TruffleString.fromCodePointUncached(decimalFormatSymbols.getGroupingSeparator(), TS_ENCODING));
            // TODO: set the proper grouping
            dict.put("mon_grouping", factory().createList());
            // TODO: reasonable default, but not the current locale setting
            dict.put("positive_sign", "");
            dict.put("negative_sign", TruffleString.fromCodePointUncached(decimalFormatSymbols.getMinusSign(), TS_ENCODING));
            dict.put("int_frac_digits", currency.getDefaultFractionDigits());
            dict.put("frac_digits", currency.getDefaultFractionDigits());
            dict.put("p_cs_precedes", PNone.NONE);
            dict.put("p_sep_by_space", PNone.NONE);
            dict.put("n_cs_precedes", PNone.NONE);
            dict.put("n_sep_by_space", PNone.NONE);
            dict.put("p_sign_posn", PNone.NONE);
            dict.put("n_sign_posn", PNone.NONE);

            return factory().createDictFromMap(dict);
        }
    }

    // _locale.setlocale(category, locale=None)
    @Builtin(name = "setlocale", minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class SetLocaleNode extends PythonBuiltinNode {

        @SuppressWarnings("fallthrough")
        @Specialization(guards = "isValidCategory(category)")
        @TruffleBoundary
        TruffleString doWithoutLocaleID(int category, @SuppressWarnings("unused") PNone posixLocaleID) {
            Locale defaultLocale;
            Locale.Category displayCategory = null;
            Locale.Category formatCategory = null;
            if (!ImageInfo.inImageBuildtimeCode()) {
                displayCategory = Locale.Category.DISPLAY;
                formatCategory = Locale.Category.FORMAT;

                switch (category) {
                    case LC_COLLATE:
                    case LC_CTYPE:
                    case LC_MESSAGES:
                        formatCategory = null;
                        break;
                    case LC_MONETARY:
                    case LC_NUMERIC:
                    case LC_TIME:
                        displayCategory = null;
                        break;
                    case LC_ALL:
                    default:
                }
                if (displayCategory != null) {
                    defaultLocale = Locale.getDefault(displayCategory);
                } else {
                    defaultLocale = Locale.getDefault(formatCategory);
                }
            } else {
                defaultLocale = Locale.getDefault();
            }

            return toPosix(defaultLocale);
        }

        @Specialization(guards = "isValidCategory(category)")
        @TruffleBoundary
        @SuppressWarnings("fallthrough")
        TruffleString doWithLocaleID(int category, TruffleString posixLocaleID) {
            Locale.Category displayCategory = null;
            Locale.Category formatCategory = null;
            if (!ImageInfo.inImageBuildtimeCode()) {
                displayCategory = Locale.Category.DISPLAY;
                formatCategory = Locale.Category.FORMAT;

                switch (category) {
                    case LC_COLLATE:
                    case LC_CTYPE:
                    case LC_MESSAGES:
                        formatCategory = null;
                        break;
                    case LC_MONETARY:
                    case LC_NUMERIC:
                    case LC_TIME:
                        displayCategory = null;
                        break;
                    case LC_ALL:
                    default:
                }
            }

            Locale newLocale = fromPosix(posixLocaleID);
            if (newLocale != null) {
                if (!ImageInfo.inImageBuildtimeCode()) {
                    if (displayCategory != null) {
                        Locale.setDefault(displayCategory, newLocale);
                    }
                    if (formatCategory != null) {
                        Locale.setDefault(formatCategory, newLocale);
                    }
                } else {
                    Locale.setDefault(newLocale);
                }
            } else {
                throw raise(PythonErrorType.ValueError, ErrorMessages.UNSUPPORTED_LOCALE_SETTING);
            }

            return toPosix(newLocale);
        }

        @Specialization(replaces = {"doWithoutLocaleID", "doWithLocaleID"})
        TruffleString doGeneric(VirtualFrame frame, Object category, Object posixLocaleID,
                        @Cached PyLongAsLongNode asLongNode,
                        @Cached CastToTruffleStringNode castToStringNode) {
            long l = asLongNode.execute(frame, category);
            if (!isValidCategory(l)) {
                throw raise(PythonErrorType.ValueError, ErrorMessages.INVALID_LOCALE_CATEGORY);
            }

            TruffleString posixLocaleIDStr = null;
            // may be NONE or NO_VALUE
            if (!PGuards.isPNone(posixLocaleID)) {
                try {
                    posixLocaleIDStr = castToStringNode.execute(posixLocaleID);
                } catch (CannotCastException e) {
                    // fall through
                }
            }

            if (posixLocaleIDStr != null) {
                return doWithLocaleID((int) l, posixLocaleIDStr);
            }
            return doWithoutLocaleID((int) l, PNone.NONE);
        }

        static boolean isValidCategory(long l) {
            return 0 <= l && l <= 6;
        }
    }
}
