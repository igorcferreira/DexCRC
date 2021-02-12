/*
 * Copyright (c) 2021 Igor Ferreira (https://igorcferreira.dev/)
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NON INFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package dev.igorcferreira.table

import android.content.Context
import java.util.zip.ZipFile

class CRCValidator {

    data class CRCResponse(val success: Boolean, val calculated: String, val entry: CRCEntry)
    data class CRCEntry(val fileName: String, val crc: String)

    companion object {
        fun validateMultipleDexFile(context: Context, table: String): List<CRCResponse> {
            val entries = expandTable(table)
            return entries.map { validateDexFile(context, it) }
        }

        private fun validateDexFile(context: Context, entry: CRCEntry): CRCResponse {
            val zipFile = ZipFile(context.packageCodePath)
            val zipEntry = zipFile.getEntry(entry.fileName)
            val crcString = String.format("%02x", zipEntry.crc)
            return CRCResponse(entry.crc == crcString, crcString, entry)
        }

        private fun expandTable(table: String): List<CRCEntry> {
            return table.split("\n")
                .mapNotNull {
                    val elements = it.split(";")
                    if (elements.size == 2) CRCEntry(elements.first(), elements.last()) else null
                }
        }
    }
}