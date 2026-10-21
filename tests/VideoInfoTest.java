package dev.clean;

public class VideoInfoTest {
    static int failed = 0;
    static void yes(boolean c, String m) { if (!c) { failed++; System.out.println("FAIL " + m); } else System.out.println("ok   " + m); }

    static final String PAGE = "<html><head></head><body><script id=\"__UNIVERSAL_DATA_FOR_REHYDRATION__\" type=\"application/json\">"
        + "{\"__DEFAULT_SCOPE__\":{\"webapp.video-detail\":{\"itemInfo\":{\"itemStruct\":{\"id\":\"7300000000000000001\","
        + "\"video\":{\"duration\":20,\"width\":576,\"height\":1024,\"playAddr\":\"https://v16.example/play.mp4\",\"downloadAddr\":\"https://v16.example/wm.mp4\",\"size\":\"2500000\","
        + "\"bitrateInfo\":["
        + "{\"GearName\":\"normal_540_0\",\"Bitrate\":900000,\"CodecType\":\"h264\",\"PlayAddr\":{\"DataSize\":\"2250000\",\"Width\":576,\"Height\":1024,\"UrlList\":[\"https://v16.example/540.mp4\",\"https://v19.example/540.mp4\"]}},"
        + "{\"GearName\":\"adapt_lowest_1080_1\",\"Bitrate\":3200000,\"CodecType\":\"bytevc1\",\"PlayAddr\":{\"DataSize\":8000000,\"Width\":1080,\"Height\":1920,\"UrlList\":[\"https://v16.example/1080.mp4\"]}},"
        + "{\"GearName\":\"lower_720_0\",\"Bitrate\":1500000,\"CodecType\":\"h264\",\"PlayAddr\":{\"Width\":720,\"Height\":1280,\"UrlList\":[\"https://v16.example/720.mp4\"]}}"
        + "]}}}}}}</script></body></html>";

    public static void main(String[] a) {
        VideoInfo v = VideoInfo.parse(PAGE);
        yes(v.error == null, "page parsed, no error: " + v.error);
        yes("7300000000000000001".equals(v.id), "video id");
        yes(v.qualities.size() == 4, "4 qualities (3 bitrates + plain playAddr): " + v.qualities.size());
        yes(v.qualities.get(0).label.startsWith("1080p") && v.qualities.get(0).label.contains("H.265"), "best first: " + v.qualities.get(0).label);
        yes(v.qualities.get(0).bytes == 8000000 && v.qualities.get(0).exact, "exact size when the page has it (number)");
        VideoInfo.Quality txt = null;
        for (VideoInfo.Quality q : v.qualities) if (q.url.endsWith("540.mp4")) txt = q;
        yes(txt != null && txt.bytes == 2250000 && txt.exact, "exact size given as text");
        yes(v.qualities.get(1).label.startsWith("720p"), "sorted by quality: " + v.qualities.get(1).label);
        VideoInfo.Quality est = null;
        for (VideoInfo.Quality q : v.qualities) if (q.url.endsWith("720.mp4")) est = q;
        yes(est != null && !est.exact && est.bytes == 1500000L * 20 / 8, "estimate = bitrate x duration / 8 when no size");
        yes(VideoInfo.sizeText(8000000, true).equals("7.6 МБ"), "size text exact: " + VideoInfo.sizeText(8000000, true));
        yes(VideoInfo.sizeText(est.bytes, false).startsWith("~"), "size text estimate has ~");
        yes(VideoInfo.sizeText(0, false).equals("размер неизвестен"), "unknown size");
        for (VideoInfo.Quality q : v.qualities) yes(!q.url.contains("wm.mp4"), "watermarked downloadAddr never offered: " + q.url);

        VideoInfo bad = VideoInfo.parse("<html>captcha</html>");
        yes(bad.error != null && bad.qualities.isEmpty(), "page without data gives a clear error");
        VideoInfo broken = VideoInfo.parse("<script id=\"__UNIVERSAL_DATA_FOR_REHYDRATION__\">{oops</script>");
        yes(broken.error != null, "broken json gives an error, no crash");
        VideoInfo nov = VideoInfo.parse("<script id=\"__UNIVERSAL_DATA_FOR_REHYDRATION__\">{\"__DEFAULT_SCOPE__\":{\"seo.abtest\":{}}}</script>");
        yes(nov.error != null, "page without video gives an error");
        yes(VideoInfo.parse(null).error != null, "null page");
        String photo = "<script id=\"__UNIVERSAL_DATA_FOR_REHYDRATION__\" type=\"application/json\">{\"__DEFAULT_SCOPE__\":{\"webapp.video-detail\":{\"itemInfo\":{\"itemStruct\":{\"id\":\"7311\",\"imagePost\":{\"images\":["
            + "{\"imageURL\":{\"urlList\":[\"https://p16.example/1.jpeg\",\"https://p19.example/1.jpeg\"]}},{\"imageURL\":{\"urlList\":[\"https://p16.example/2.jpeg\"]}},{\"imageURL\":{\"urlList\":[]}}]},"
            + "\"video\":{\"duration\":0,\"playAddr\":\"\"}}}}}}</script>";
        VideoInfo ph = VideoInfo.parse(photo);
        yes(ph.error == null && ph.images.size() == 2 && ph.qualities.isEmpty(), "photo post: 2 pictures, no video, no error: " + ph.images);
        yes(ph.images.get(0).equals("https://p16.example/1.jpeg") && "7311".equals(ph.id), "first link of each picture, id kept");
        yes(v.images.isEmpty(), "normal video has no pictures");

        yes("https://vm.tiktok.com/ZMabc123/".equals(VideoInfo.findLink("Смотри: https://vm.tiktok.com/ZMabc123/ классно")), "link inside text");
        yes("https://www.tiktok.com/@u/video/7300000000000000001".equals(VideoInfo.findLink("(https://www.tiktok.com/@u/video/7300000000000000001).")), "trailing ) and . removed");
        yes(VideoInfo.findLink("https://example.com/x") == null && VideoInfo.findLink("нет ссылки") == null && VideoInfo.findLink(null) == null, "other texts give no link");
        yes("7300000000000000001".equals(VideoInfo.videoId("https://www.tiktok.com/@u/video/7300000000000000001?x=1")), "id from url");
        if (failed > 0) { System.out.println(failed + " failed"); System.exit(1); }
        System.out.println("ALL PASSED");
    }
}
