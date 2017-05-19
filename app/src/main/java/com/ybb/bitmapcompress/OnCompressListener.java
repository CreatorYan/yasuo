package com.ybb.bitmapcompress;

import java.io.File;

public interface OnCompressListener {
    void onSuccess(File file);

    void onError(String e);
}
