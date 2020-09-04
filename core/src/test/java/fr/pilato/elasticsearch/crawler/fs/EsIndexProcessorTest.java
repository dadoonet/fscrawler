package fr.pilato.elasticsearch.crawler.fs;

import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;

public class EsIndexProcessorTest {

    @Test
    public void mergeExtraDoc() {
        Map<String,Object> extra = new HashMap<>();
        Map<String,Object> geo = new HashMap<>();
        geo.put("hello", "world");
        extra.put("geo", geo);
        String json = "{\"file\" : {\"extension\" : \"pdf\"} }";
        String modifiedJson = new EsIndexProcessor().mergeExtraDoc(json, extra);
        assertEquals("{\"file\":{\"extension\":\"pdf\"},\"geo\":{\"hello\":\"world\"}}", modifiedJson);
    }
}