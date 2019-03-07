# AndroidThaiNationalIDCard
Thai National ID Card library for Android application

##### How to install?
- create your project
- add new module by module type 'Android Library' and module package name 'co.advancedlogic.thainationalidcard'
- copy java,res directory and file AndroidManifest.xml from this project sourcecode to your project
  into directory (new module name)/src/main/

##### How to use?
###### use this example code in button click event or somewhere after your activity is loaded.

```java
SmartCardDevice device = SmartCardDevice.getSmartCardDevice(getApplicationContext(), "Smart Card", new SmartCardDevice.SmartCardDeviceEvent() {
    @Override
    public void OnReady(SmartCardDevice device) {
        ThaiSmartCard thaiSmartCard = new ThaiSmartCard(device);

        ThaiSmartCard.PersonalInformation info = thaiSmartCard.getPersonalInformation();

        if (info == null) {
            Toast.makeText(getApplicationContext(), "Read Smart Card information failed", Toast.LENGTH_LONG).show();
            return;
        }

        Log.d("SmartCard", String.format("PID: %s NameTH: %s NameEN: %s BirthDate: %s", info.PersonalID, info.NameTH, info.NameEN, info.BirthDate));

        Bitmap personalPic = thaiSmartCard.getPersonalPicture();

        if (personalPic == null) {
            Toast.makeText(getApplicationContext(), "Read Smart Card personal picture failed", Toast.LENGTH_LONG).show();
            return;
        }

        // do something
    }

    @Override
    public void OnDetached(SmartCardDevice device) {
        Toast.makeText(getApplicationContext(), "Smart Card is removed", Toast.LENGTH_LONG).show();
    }
});

if (device == null) {
    Toast.makeText(getApplicationContext(), "Smart Card device not found", Toast.LENGTH_LONG).show();
}

```
