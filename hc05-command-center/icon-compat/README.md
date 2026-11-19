# Vytal Android icon compatibility

Replace `app/src/main/res/drawable/ic_vytal_launcher.xml` with your own Android VectorDrawable XML, keeping the resource name `ic_vytal_launcher`, or add PNG launcher assets under `mipmap-mdpi`, `mipmap-hdpi`, `mipmap-xhdpi`, `mipmap-xxhdpi`, and `mipmap-xxxhdpi` and update `AndroidManifest.xml` to reference them. Rebuild the APK after changing icon assets; an installed APK cannot load arbitrary icon files from this folder.

The launcher currently references `@drawable/ic_vytal_launcher`. Keep that name for the simplest replacement.