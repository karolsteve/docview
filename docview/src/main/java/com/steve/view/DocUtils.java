/*
 * Copyright 2018 Steve Tchatchouang
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package com.steve.view;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.pdf.PdfRenderer;
import android.os.ParcelFileDescriptor;

import java.io.File;

/**
 * Created by Steve Tchatchouang on 17/01/2018
 */

public class DocUtils {
    static Bitmap getBitmapFromPdf(String path, int page, boolean preview) throws Exception {

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            ParcelFileDescriptor descriptor = ParcelFileDescriptor.open(new File(path), ParcelFileDescriptor.MODE_READ_ONLY);
            PdfRenderer renderer = new PdfRenderer(descriptor);
            PdfRenderer.Page p = renderer.openPage(page);
            int width = preview ? 320 : p.getWidth();
            int height = preview ? 240 : p.getHeight();
            final Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);
            canvas.drawColor(Color.WHITE);
            p.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT);
            p.close();
            renderer.close();
            descriptor.close();
            return bitmap;
        }
        return Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888);
    }

    public static Bitmap getBitmapPreview(File file) throws Exception {
        return getBitmapFromPdf(file.getAbsolutePath(), 0,true);
    }
}
