# react-native-get-mms-android

## Getting started

`$ npm install react-native-get-mms-android --save`

### Mostly automatic installation

`$ react-native link react-native-get-mms-android`

## Usage
```javascript
    import GetMmsAndroid from 'react-native-get-mms-android';
```

## Methods

### countMMS(threadId, callback)
Get the mms count for a thread

### getMMS(threadId, errorCallback, successCallback)
Get all mms for a thread

### getMMSWithIdPublic(mmsId, callback)
Get mms data for a mms

### getMMSImagePublic(mmsId, callback)
Get mms image for a mms (base64)
