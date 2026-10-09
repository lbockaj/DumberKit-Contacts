# ez-vcard
-keep,includedescriptorclasses class ezvcard.property.** { *; }
-keep enum ezvcard.VCardVersion { *; }
-dontwarn ezvcard.io.json.**
-dontwarn freemarker.**
-keep class ezvcard.parameter.** {
    <init>(...);
}

# Customization screen: its themes keep the launcher icon color (helpers/KeepIconColor.kt)
-keepclassmembers class org.fossify.commons.activities.CustomizationActivity {
    private java.util.LinkedHashMap predefinedThemes;
    private int curAppIconColor;
}
