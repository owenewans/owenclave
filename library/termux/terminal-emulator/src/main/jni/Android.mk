LOCAL_PATH:= $(call my-dir)
include $(CLEAR_VARS)
LOCAL_MODULE:= libtermux
LOCAL_SRC_FILES:= termux.cpp
include $(BUILD_SHARED_LIBRARY)
APP_SUPPORT_FLEXIBLE_PAGE_SIZES := true
