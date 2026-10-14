# Add project specific ProGuard rules here.

# Anthropic Java SDK（写真の読み取り）。内部の Jackson がリフレクションで型を読むので、圧縮で消さない
-keep class com.anthropic.** { *; }
-keep class com.fasterxml.jackson.** { *; }
-keep class kotlin.Metadata { *; }
-keepattributes Signature,*Annotation*,InnerClasses,EnclosingMethod
# Android にない Java の標準クラスへの参照。Jackson の Java Beans 対応と、
# クラスから JSON スキーマを自動生成する機能（このアプリは使わず、スキーマを手で渡している）からのもの
-dontwarn java.beans.ConstructorProperties
-dontwarn java.beans.Transient
-dontwarn java.lang.reflect.AnnotatedParameterizedType
-dontwarn java.lang.reflect.AnnotatedType
