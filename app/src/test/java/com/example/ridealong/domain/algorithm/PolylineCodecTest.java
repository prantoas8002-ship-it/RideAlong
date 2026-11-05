package com.example.ridealong.domain.algorithm;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.example.ridealong.domain.model.GeoCoord;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class PolylineCodecTest {

    @Test
    public void decodesGoogleReferenceExample() {
        // From Google's polyline algorithm documentation.
        List<GeoCoord> pts = PolylineCodec.decode("_p~iF~ps|U_ulLnnqC_mqNvxq`@");
        assertEquals(3, pts.size());
        assertEquals(38.5, pts.get(0).lat, 1e-5);
        assertEquals(-120.2, pts.get(0).lng, 1e-5);
        assertEquals(40.7, pts.get(1).lat, 1e-5);
        assertEquals(-126.453, pts.get(2).lng, 1e-5);
    }

    @Test
    public void encodeDecodeRoundTrip() {
        List<GeoCoord> in = Arrays.asList(new GeoCoord(23.80689, 90.36871), new GeoCoord(23.7389, 90.395),
                new GeoCoord(-33.86, 151.2));
        List<GeoCoord> out = PolylineCodec.decode(PolylineCodec.encode(in));
        assertEquals(in.size(), out.size());
        for (int i = 0; i < in.size(); i++) {
            assertEquals(in.get(i).lat, out.get(i).lat, 1e-5);
            assertEquals(in.get(i).lng, out.get(i).lng, 1e-5);
        }
    }

    @Test
    public void malformedInputDoesNotCrash() {
        assertTrue(PolylineCodec.decode("_p~iF~ps|U_").size() <= 2);
        assertEquals(0, PolylineCodec.decode("").size());
        assertEquals(0, PolylineCodec.decode(null).size());
    }

    @Test
    public void simplifyKeepsEndpointsAndShape() {
        List<GeoCoord> line = new ArrayList<>();
        for (int i = 0; i <= 100; i++) line.add(new GeoCoord(0, i * 0.001)); // straight
        line.add(new GeoCoord(0.05, 0.1)); // corner
        List<GeoCoord> s = PolylineCodec.simplify(line, 0.01, 300);
        assertEquals(line.get(0), s.get(0));
        assertEquals(line.get(line.size() - 1), s.get(s.size() - 1));
        assertTrue(s.size() <= 4);
    }

    @Test
    public void simplifyCapsPointCount() {
        List<GeoCoord> zigzag = new ArrayList<>();
        for (int i = 0; i < 1000; i++) zigzag.add(new GeoCoord((i % 2) * 0.01, i * 0.001));
        assertTrue(PolylineCodec.simplify(zigzag, 0.0001, 50).size() <= 50);
    }
}
