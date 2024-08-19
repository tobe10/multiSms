package com.exemple.multisms

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Parcelable
import android.telephony.SmsManager
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.parcelize.Parcelize
import org.apache.poi.ss.usermodel.Cell
import org.apache.poi.ss.usermodel.CellType
import org.apache.poi.ss.usermodel.WorkbookFactory
import java.io.InputStream
@Parcelize
data class Contact(val phoneNumber: String, val customValues: List<String>):Parcelable

fun getInputStreamFromUri(context: Context, uri: Uri): InputStream? {
    return context.contentResolver.openInputStream(uri)
}

fun readExcelFromUri(context: Context, uri: Uri): List<Contact> {
    val inputStream = getInputStreamFromUri(context, uri)
    return if (inputStream != null) {
        readExcel(inputStream)
    } else {
        emptyList()
    }
}


fun readExcel(inputStream: InputStream?): List<Contact> {
    val contacts = mutableListOf<Contact>()

    val workbook = WorkbookFactory.create(inputStream)
    val sheet = workbook.getSheetAt(0)
    for (row in sheet) {
        val phoneNumber = getCellStringValue(row.getCell(0))
        val customValues = row.cellIterator().asSequence().drop(1).map {
            getCellStringValue(it)
        }.toList()
        contacts.add(Contact(phoneNumber, customValues))
    }
    inputStream?.close()
    return contacts
}

fun getCellStringValue(cell: Cell?): String {
    return when (cell?.cellType) {
        CellType.STRING -> cell.stringCellValue
        CellType.NUMERIC -> cell.numericCellValue.toString()
        CellType.BOOLEAN -> cell.booleanCellValue.toString()
        CellType.FORMULA -> {
            when (cell.cachedFormulaResultType) {
                CellType.STRING -> cell.stringCellValue
                CellType.NUMERIC -> cell.numericCellValue.toString()
                CellType.BOOLEAN -> cell.booleanCellValue.toString()
                else -> ""
            }
        }

        else -> ""
    }
}



