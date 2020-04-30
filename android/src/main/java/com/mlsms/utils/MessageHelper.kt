package com.mlsms.utils

import android.content.ContentResolver
import android.content.ContentUris
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.provider.ContactsContract
import android.util.Log
import com.mysmsbook.MainApplication
import com.mysmsbook.core.data.Message
import com.mysmsbook.core.data.PhoneContact
import com.smsandco.monlivresms.R
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.lang.Long
import java.util.*
import kotlin.collections.ArrayList

/**
 * Created by arnaud on 06/02/2017.
 */
object MessageHelper {
    private val TAG = MessageHelper::class.java.simpleName
    private val contentResolver: ContentResolver = MainApplication.get().contentResolver

    val contacts = ArrayList<PhoneContact>()
    val conversations = TreeMap<PhoneContact, List<Message>>()

    var selectedContact: PhoneContact? = null
    var currentConversation = ArrayList<Message>()
    private val mmsImagesUri = HashMap<Int, java.util.ArrayList<Uri>>()

    fun clearAll() {
        contacts.clear()
        conversations.clear()
        mmsImagesUri.clear()
        clearSelection()
    }

    fun clearSelection() {
        currentConversation.clear()
        selectedContact = null
    }

    fun setupDevData() {
        val msg: List<Message>
        val ts = Date().time
        val max = MainApplication.get().resources.getInteger(R.integer.random_messages)

        msg = (1..max).map { i ->
            val message = (1..3).map { UUID.randomUUID().toString() }.joinToString()
            Message(timestamp = 1, fromNumber = "${ts + i}", text = message, type = Message.SMSType.SENT)
        }
        val contact = PhoneContact(id = 1234, displayName = "test contact", phoneNumber = "123456789", numberOfMessagesExchanged = msg.size)

        conversations[contact] = msg
        contacts += contact
    }

    fun addConversation(phoneContact: PhoneContact, messages: List<Message>) {
        if (conversations[phoneContact] == null) {
            conversations[phoneContact] = ArrayList<Message>()
        }
        val list = conversations[phoneContact] as ArrayList<Message>
        list += messages
        conversations[phoneContact] = list
    }

    fun selectContactAndFillConversation(phoneContact: PhoneContact) {
        selectedContact = phoneContact
        currentConversation.clear()
        currentConversation.addAll(conversations[phoneContact]!!)
    }

    fun getFullConversation(phoneContact: PhoneContact) = conversations[phoneContact]!!

    fun countConversation(): Int {
        val cursor = cursor()
        val count = cursor?.count ?: 0
        cursor?.close()
        return count
    }

    fun cursor(): Cursor? {
        val uri = Uri.parse("content://mms-sms/conversations?simple=true")
        val projection = arrayOf("_id")
        val cursor = contentResolver.query(uri, projection, null, null, null)
        return cursor
    }

    fun fillConversation(cursor: Cursor) {
        val extracted = extractConversation(cursor)
        if (extracted != null) {
            val (contact: com.mysmsbook.core.data.PhoneContact?, conversation) = extracted
            if (contact != null) {
                // Save conversation for contact
                addConversation(contact, conversation)
            }
        }
    }

    private fun extractConversation(cursor: Cursor): Pair<PhoneContact?, List<Message>>? {
        // Get all messages in thread
        //val allMessages = getSMSAndMMS(cursor.getInt(cursor.getColumnIndex("_id")))

        val threadId = cursor.getInt(cursor.getColumnIndex("_id"))
        val first = getFirstReceivedSMS(threadId)
        if (first != null) {
            val contact: PhoneContact? = getPhoneContact(first.fromNumber!!)
            if (contact != null) {
                contact.numberOfMessagesExchanged = (countSMS(threadId) + countMMS(threadId))
                contact.threadId = threadId
            }
            return Pair(contact, ArrayList<Message>())
        } else {
            return null
        }
    }

    fun countSMS(threadId: Int): Int {
        val selection = "thread_id=" + threadId
        val uri = Uri.parse("content://sms")
        val cursor = contentResolver.query(uri, null, selection, null, null)
        val result = cursor!!.count
        cursor.close()
        return result
    }

    fun countMMS(threadId: Int): Int {
        val selection = "thread_id=" + threadId
        val uri = Uri.parse("content://mms")
        val cursor = contentResolver.query(uri, null, selection, null, null)
        val result = cursor!!.count
        cursor.close()
        return result
    }

    fun getFirstReceivedSMS(threadId: Int): Message? {
        val selection = "thread_id=" + threadId
        val uri = Uri.parse("content://sms/inbox")
        val cursor = contentResolver.query(uri, null, selection, null, null)
        if (cursor!!.moveToFirst()) {
            return smsCursorToMessage(cursor, Message.SMSType.RECEIVED)
        }
        cursor.close()
        return null
    }

    /*private fun extractConversation(cursor: Cursor): Pair<PhoneContact?, List<Message>>? {
        // Get all messages in thread
        val allMessages = getSMSAndMMS(cursor.getInt(cursor.getColumnIndex("_id")))

        val first = allMessages.asSequence().filter { it.fromNumber != null && it.fromNumber!!.isNotEmpty() }.firstOrNull()
        if (first != null) {
            val contact: PhoneContact? = getPhoneContact(first.fromNumber!!)
            if (contact != null) {
                contact.numberOfMessagesExchanged = allMessages.size
            }
            return Pair(contact, allMessages)
        } else {
            return null
        }
    }*/

    fun getSMSAndMMS(threadId: Int?): ArrayList<Message> {
        val allMessages = ArrayList<Message>()

        // Get received SMS in given thread
        val receivedSMSs = getReceivedSMS(threadId)
        allMessages.addAll(receivedSMSs)

        // Get sent SMS in given thread
        val sentSMSs = getSentSMS(threadId)
        allMessages.addAll(sentSMSs)

        // Get received MMS in given thread
        val receivedMMSs = getReceivedMMS(threadId)
        allMessages.addAll(receivedMMSs)

        // Get sent MMS in given thread
        val sentMMSs = getSentMMS(threadId)
        allMessages.addAll(sentMMSs)

        return allMessages
    }

    fun getReceivedSMS(threadId: Int?): ArrayList<Message> {
        val receivedMessages = ArrayList<Message>()
        val selection = "thread_id=" + threadId
        val uri = Uri.parse("content://sms/inbox")
        val cursor = contentResolver.query(uri, null, selection, null, null)
        if (cursor!!.moveToFirst()) {
            do {
                val receivedMessage = smsCursorToMessage(cursor, Message.SMSType.RECEIVED)
                receivedMessages.add(receivedMessage)
            } while (cursor!!.moveToNext())
        }
        cursor!!.close()
        return receivedMessages
    }

    fun getSentSMS(threadId: Int?): ArrayList<Message> {
        val sentMessages = ArrayList<Message>()
        val selection = "thread_id=" + threadId
        val uri = Uri.parse("content://sms/sent")
        val cursor = contentResolver.query(uri, null, selection, null, null)
        if (cursor!!.moveToFirst()) {
            do {
                val sentMessage = smsCursorToMessage(cursor, Message.SMSType.SENT)
                sentMessages.add(sentMessage)
            } while (cursor!!.moveToNext())
        }
        cursor!!.close()
        return sentMessages
    }

    fun smsCursorToMessage(cursor: Cursor, type: Message.SMSType): Message {
        val message = Message()
        val timestamp = Long.parseLong(cursor.getString(cursor.getColumnIndex("date")))
        message.timestamp = timestamp
        message.fromNumber = cursor.getString(cursor.getColumnIndex("address"))
        message.text = cursor.getString(cursor.getColumnIndex("body"))
        message.type = type
        return message
    }

    fun getReceivedMMS(threadId: Int?): ArrayList<Message> {
        val receivedMessages = ArrayList<Message>()
        val selection = "thread_id=" + threadId
        val uri = Uri.parse("content://mms/inbox")
        val cursor = contentResolver.query(uri, null, selection, null, null)
        if (cursor!!.moveToFirst()) {
            do {
                val mmsId = cursor!!.getInt(cursor!!.getColumnIndex("_id"))

                // Get MMS Part data
                val mms = getMMSWithId(mmsId, Message.SMSType.RECEIVED)
                if (mms != null) {
                    // Get date
                    val timestamp = Long.parseLong(cursor!!.getString(cursor!!.getColumnIndex("date")))
                    mms.timestamp = timestamp * 1000

                    receivedMessages.add(mms)
                }
            } while (cursor!!.moveToNext())
        }
        cursor!!.close()
        return receivedMessages
    }

    fun getSentMMS(threadId: Int?): ArrayList<Message> {
        val sentMessages = ArrayList<Message>()
        val selection = "thread_id=" + threadId
        val uri = Uri.parse("content://mms/sent")
        val cursor = contentResolver.query(uri, null, selection, null, null)
        if (cursor!!.moveToFirst()) {
            do {
                val mmsId = cursor!!.getInt(cursor!!.getColumnIndex("_id"))

                // Get MMS Part data
                val mms = getMMSWithId(mmsId, Message.SMSType.SENT)
                if (mms != null) {
                    // Get date
                    val timestamp = Long.parseLong(cursor!!.getString(cursor!!.getColumnIndex("date")))
                    mms.timestamp = timestamp * 1000

                    sentMessages.add(mms)
                }
            } while (cursor!!.moveToNext())
        }
        cursor!!.close()
        return sentMessages
    }

    fun getMMSWithId(mmsId: Int, type: Message.SMSType): Message? {
        var mms: Message? = null
        val selectionPart = "mid=" + mmsId
        val uri = Uri.parse("content://mms/part")
        val cursor = contentResolver.query(uri, null,
                selectionPart, null, null)
        if (cursor!!.moveToFirst()) {
            mms = Message()
            // Get Address
            val address = getMmsAddress(mmsId)
            mms.fromNumber = address ?: ""

            // Set SMSType
            mms.type = type

            // Get parts
            do {
                val partId = cursor!!.getString(cursor!!.getColumnIndex("_id"))
                val contentType = cursor!!.getString(cursor!!.getColumnIndex("ct"))

                // Get message
                var body: String?
                if ("text/plain" == contentType) {
                    val data = cursor!!.getString(cursor!!.getColumnIndex("_data"))
                    if (data != null) {
                        body = getMmsText(partId)
                    } else {
                        body = cursor!!.getString(cursor!!.getColumnIndex("text"))
                    }
                    mms.text = body
                } else if (isImageType(contentType) == true) {
                    val imageUri = Uri.parse("content://mms/part/" + partId)
                    addMMSImageUri(mmsId, imageUri)
                    mms.mmsId = mmsId
                }// Get image URI
            } while (cursor!!.moveToNext())
        }
        cursor!!.close()
        return mms
    }

    fun getMmsText(id: String): String {
        val partURI = Uri.parse("content://mms/part/" + id)
        var `is`: InputStream? = null
        val sb = StringBuilder()
        try {
            `is` = contentResolver.openInputStream(partURI)
            if (`is` != null) {
                val isr = InputStreamReader(`is`, "UTF-8")
                val reader = BufferedReader(isr)
                var temp: String? = reader.readLine()
                while (temp != null) {
                    sb.append(temp)
                    temp = reader.readLine()
                }
            }
        } catch (e: IOException) {
        } finally {
            if (`is` != null) {
                try {
                    `is`.close()
                } catch (e: IOException) {
                }

            }
        }
        return sb.toString()
    }

    private fun isImageType(mime: String): Boolean {
        var result = false
        if (mime.equals("image/jpg", ignoreCase = true)
                || mime.equals("image/jpeg", ignoreCase = true)
                || mime.equals("image/png", ignoreCase = true)
                || mime.equals("image/gif", ignoreCase = true)
                || mime.equals("image/bmp", ignoreCase = true)) {
            result = true
        }
        return result
    }

    fun getMmsAddress(mmsId: Int): String? {
        var address: String? = null
        // Get Address
        val uriAddrPart = Uri.parse("content://mms/$mmsId/addr")
        val cursor = contentResolver.query(uriAddrPart, null, null, null, null)
        if (cursor != null && cursor!!.moveToLast()) {
            do {
                val addColIndx = cursor!!.getColumnIndex("address")
                address = cursor!!.getString(addColIndx)
            } while (cursor!!.moveToPrevious())
        }
        cursor!!.close()
        return address
    }

    fun getPhoneContact(fullContactNumber: String): PhoneContact {
        var contactNumber = fullContactNumber
        val numLength = contactNumber.length
        val countryNumLength = MainApplication.get().getResources().getInteger(R.integer.number_of_digit_in_phone_number)
        if (numLength > countryNumLength) {
            val numberOfDigitToRemove = numLength - countryNumLength + 1
            try {
                contactNumber = contactNumber.substring(numberOfDigitToRemove)
            } catch (e: IndexOutOfBoundsException) {
            }

        } else if (numLength == countryNumLength) {
            try {
                contactNumber = contactNumber.substring(1)
            } catch (e: IndexOutOfBoundsException) {
            }

        }
        val contact = getContactWithNumber(contactNumber)
        if (contact != null) {
            return contact
        } else {
            val unknownContact = PhoneContact()
            unknownContact.displayName = fullContactNumber
            unknownContact.phoneNumber = contactNumber
            unknownContact.pictureUri = null
            return unknownContact
        }

    }

    fun getContactWithNumber(phoneNumber: String): PhoneContact? {
        val phoneUri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
        val phoneNumCursor = contentResolver.query(phoneUri, null, null, null, null)
        var contact: PhoneContact? = null
        if (phoneNumCursor!!.getCount() > 0) {
            while (phoneNumCursor!!.moveToNext()) {
                var phoneNum = phoneNumCursor!!.getString(phoneNumCursor!!.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER))
                if (phoneNum.length > 0) {
                    phoneNum = phoneNum.trim({ it <= ' ' }).replace(" ", "").substring(1)
                    if (phoneNum.contains(phoneNumber)) {
                        contact = PhoneContact()
                        val contactId = phoneNumCursor!!.getString(phoneNumCursor!!.getColumnIndex(ContactsContract.CommonDataKinds.Phone.CONTACT_ID))
                        val contactUri = ContactsContract.Contacts.CONTENT_URI
                        if (Integer.valueOf(Build.VERSION.SDK_INT) > 10) {
                            val projection = arrayOf(ContactsContract.Contacts._ID, ContactsContract.Contacts.DISPLAY_NAME, ContactsContract.Contacts.HAS_PHONE_NUMBER, ContactsContract.Contacts.PHOTO_URI)
                            val selectionContact = ContactsContract.Contacts._ID + " = ? "
                            val contactCursor = MainApplication.get().getContentResolver().query(contactUri, projection, selectionContact, arrayOf<String>(contactId), null)
                            if (contactCursor!!.moveToFirst()) {

                                val displayName = contactCursor!!.getString(contactCursor!!.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME))
                                val photo = contactCursor!!.getString(contactCursor!!
                                        .getColumnIndex(ContactsContract.CommonDataKinds.Phone.PHOTO_URI))
                                contact.id = Long.parseLong(contactId)
                                contact.displayName = displayName
                                contact.phoneNumber = phoneNum
                                if (photo != null)
                                    contact.pictureUri = Uri.parse(photo)
                                contactCursor!!.close()
                                break
                            } else {
                                contactCursor!!.close()
                                contact = null
                            }
                        } else {
                            val projection = arrayOf(ContactsContract.Contacts._ID, ContactsContract.Contacts.DISPLAY_NAME, ContactsContract.Contacts.HAS_PHONE_NUMBER, ContactsContract.Contacts.PHOTO_ID)
                            val selectionContact = ContactsContract.Contacts._ID + " LIKE ? "
                            val contactCursor = MainApplication.get().getContentResolver().query(contactUri, projection, selectionContact, arrayOf<String>(contactId), null)
                            if (contactCursor!!.moveToFirst()) {

                                val displayName = contactCursor!!.getString(contactCursor!!.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME))
                                val photo = contactCursor!!.getLong(contactCursor!!
                                        .getColumnIndex(ContactsContract.Contacts.PHOTO_ID)) as Int
                                contact.id = Long.parseLong(contactId)
                                contact.displayName = displayName
                                contact.phoneNumber = phoneNum
                                if (photo != 0)
                                    contact.pictureUri = ContentUris.withAppendedId(ContactsContract.Data.CONTENT_URI, photo.toLong())
                                contactCursor!!.close()
                                break
                            } else {
                                contactCursor!!.close()
                                contact = null
                            }
                        }
                    }
                }
            }
            phoneNumCursor!!.close()
            return contact
        } else {
            phoneNumCursor!!.close()
            return null
        }
    }


    fun addMMSImageUri(mmsId: Int, uri: Uri) {
        if (mmsImagesUri[mmsId] == null) {
            val mmsImagesUris = java.util.ArrayList<Uri>()
            mmsImagesUri.put(mmsId, mmsImagesUris)
        }
        val mmsImagesUris = mmsImagesUri[mmsId]!!
        if (!mmsImagesUris.contains(uri)) {
            mmsImagesUris.add(uri)
            this.mmsImagesUri.put(mmsId, mmsImagesUris)
        }
    }

    fun getMMSImageUri(mmsId: Int): java.util.ArrayList<Uri> {
        return this.mmsImagesUri[mmsId]!!
    }
}
