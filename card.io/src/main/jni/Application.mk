# card.io Application.mk
#
# See the file "LICENSE.md" for the full license governing this code.

APP_STL := gnustl_static
APP_PLATFORM := android-16

NDK_TOOLCHAIN_VERSION := clang

## RELEASE

# Build both all native supported architectures... 
# BUT, we will make a runtime decision to use the vision module.
APP_ABI := armeabi-v7a arm64-v8a x86 x86_64
APP_CFLAGS += -O3

## DEBUG
#APP_ABI := armeabi-v7a
#APP_CFLAGS += -UNDEBUG -O0 -g -ggdb 
#APP_OPTIM := debug
#APP_CPPFLAGS += -DDMZ_DEBUG=1


# disable "mangling of 'va_list' has changed in GCC 4.4" warning.
#APP_CFLAGS += -Wno-psabi

APP_CPPFLAGS += -DANDROID_DMZ=1
