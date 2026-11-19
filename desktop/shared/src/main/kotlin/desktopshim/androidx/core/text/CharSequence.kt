package androidx.core.text

fun CharSequence.isDigitsOnly(): Boolean = isNotEmpty() && all { it.isDigit() }

