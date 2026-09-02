# Implementation Plan - Google Sites Style UI

Update the app's interface to be clean and simple, resembling a basic Google Sites page with "Hola Mundo" as the central element.

## Proposed Changes

### [app](file:///C:/Users/femur/AndroidStudioProjects/Reto0/app)

#### [MODIFY] [activity_main.xml](file:///C:/Users/femur/AndroidStudioProjects/Reto0/app/src/main/res/layout/activity_main.xml)
- Remove the `FloatingActionButton` to simplify the UI.

#### [MODIFY] [MainActivity.kt](file:///C:/Users/femur/AndroidStudioProjects/Reto0/app/src/main/java/com/example/reto0/MainActivity.kt)
- Remove the `binding.fab` click listener to match the layout changes.

#### [MODIFY] [fragment_first.xml](file:///C:/Users/femur/AndroidStudioProjects/Reto0/app/src/main/res/layout/fragment_first.xml)
- Remove the "Next" button.
- Center the "Hola Mundo" text and apply a large headline style (`headlineLarge`).
- Center the content vertically and horizontally.

#### [MODIFY] [strings.xml](file:///C:/Users/femur/AndroidStudioProjects/Reto0/app/src/main/res/values/strings.xml)
- Clean up the `lorem_ipsum` string resource.
- Update `app_name` if needed (optional, keeping it as "Reto 0" for now).

## Verification Plan

### Automated Tests
- Run `gradlew assembleDebug` to ensure the project still compiles.

### Manual Verification
- Deploy the app to a device/emulator and verify the "Hola Mundo" text is centered and prominently displayed.
