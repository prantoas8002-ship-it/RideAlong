package com.example.ridealong.utils;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.net.Uri;
import android.util.Base64;

import androidx.annotation.Nullable;
import androidx.exifinterface.media.ExifInterface;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Profile picture pipeline (no Firebase Storage on the Spark plan):
 * decode with subsampling → fix EXIF rotation → center-crop to a square → scale to 256x256 →
 * JPEG, lowering quality until under ~100 KB → Base64 for the user document.
 */
public final class ImageUtils {
    private ImageUtils() { }

    @Nullable
    public static String uriToAvatarBase64(Context context, Uri uri, int sizePx, int maxBytes) throws IOException {
        Bitmap decoded = decodeSampled(context, uri, sizePx * 2);
        if (decoded == null) return null;
        Bitmap rotated = applyExifRotation(context, uri, decoded);
        Bitmap square = centerCropSquare(rotated, sizePx);
        byte[] jpeg = compressUnder(square, maxBytes);
        if (square != rotated) square.recycle();
        if (rotated != decoded) rotated.recycle();
        decoded.recycle();
        return Base64.encodeToString(jpeg, Base64.NO_WRAP);
    }

    @Nullable
    public static byte[] decodeBase64(@Nullable String b64) {
        if (b64 == null || b64.isEmpty()) return null;
        try {
            return Base64.decode(b64, Base64.DEFAULT);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static Bitmap decodeSampled(Context c, Uri uri, int targetPx) throws IOException {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        try (InputStream in = c.getContentResolver().openInputStream(uri)) {
            BitmapFactory.decodeStream(in, null, bounds);
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null;
        int sample = 1;
        int minSide = Math.min(bounds.outWidth, bounds.outHeight);
        while (minSide / (sample * 2) >= targetPx) sample *= 2;
        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inSampleSize = sample;
        try (InputStream in = c.getContentResolver().openInputStream(uri)) {
            return BitmapFactory.decodeStream(in, null, opts);
        }
    }

    private static Bitmap applyExifRotation(Context c, Uri uri, Bitmap bmp) {
        int degrees = 0;
        try (InputStream in = c.getContentResolver().openInputStream(uri)) {
            if (in != null) {
                ExifInterface exif = new ExifInterface(in);
                int o = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
                if (o == ExifInterface.ORIENTATION_ROTATE_90) degrees = 90;
                else if (o == ExifInterface.ORIENTATION_ROTATE_180) degrees = 180;
                else if (o == ExifInterface.ORIENTATION_ROTATE_270) degrees = 270;
            }
        } catch (IOException ignored) {
            // No EXIF: keep orientation as decoded.
        }
        if (degrees == 0) return bmp;
        Matrix m = new Matrix();
        m.postRotate(degrees);
        return Bitmap.createBitmap(bmp, 0, 0, bmp.getWidth(), bmp.getHeight(), m, true);
    }

    static Bitmap centerCropSquare(Bitmap src, int size) {
        int side = Math.min(src.getWidth(), src.getHeight());
        int x = (src.getWidth() - side) / 2;
        int y = (src.getHeight() - side) / 2;
        Bitmap cropped = Bitmap.createBitmap(src, x, y, side, side);
        if (side == size) return cropped;
        Bitmap scaled = Bitmap.createScaledBitmap(cropped, size, size, true);
        if (cropped != src && cropped != scaled) cropped.recycle();
        return scaled;
    }

    private static byte[] compressUnder(Bitmap bmp, int maxBytes) {
        int quality = 90;
        byte[] out;
        do {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            bmp.compress(Bitmap.CompressFormat.JPEG, quality, bos);
            out = bos.toByteArray();
            quality -= 10;
        } while (out.length > maxBytes && quality >= 30);
        return out;
    }
}
