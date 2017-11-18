package hello;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.solomon.common.util.Constants;
import com.solomon.dm.client.service.collect.GwCollectMgtService;
import com.solomon.man.common.tools.GwErrorCodeEnum;
import com.solomon.man.service.HighchartsExportService;
import com.solomon.man.service.storage.StoragePicture;
import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.toList;
import static org.apache.commons.lang3.StringUtils.join;

@Service
public class HighchartsExportServiceImpl implements HighchartsExportService {
    private static final Logger logger = LoggerFactory.getLogger(HighchartsExportServiceImpl.class);

    private static final MediaType APPLICATION_JSON_UTF8 = MediaType.parse("application/json; charset=utf-8");
    private static final String IMAGE_TYPE = "?type=png";

    private final String OS_TYPE = System.getProperty("os.name").toLowerCase();

    private final String EXPORT_DIRECTORY = OS_TYPE.startsWith("win")
            ? this.getClass().getResource("/").getPath().substring(1)
            : this.getClass().getResource("/").getPath();

    private OkHttpClient client = new OkHttpClient();

    private final StoragePicture storagePicture;
    private final GwCollectMgtService gwCollectMgtService;

    @Autowired
    public HighchartsExportServiceImpl(StoragePicture storagePicture, GwCollectMgtService gwCollectMgtService) {
        this.storagePicture = storagePicture;
        this.gwCollectMgtService = gwCollectMgtService;
    }

    /**
     * 向导出服务器发送请求，导出服务器返回指定格式的图片
     * @param url  导出服务器地址
     * @param payload  JSON格式的请求参数
     * @param fileName  生成的文件名称
     * @throws IOException
     */
    private void generateImage(String url, String payload, String fileName) throws IOException {
        RequestBody body = RequestBody.create(APPLICATION_JSON_UTF8, payload);
        Request request = new Request.Builder()
                .url(url)
                .post(body)
                .build();
        Response response = client.newCall(request).execute();
        FileOutputStream fos = new FileOutputStream(EXPORT_DIRECTORY + fileName);
        fos.write(response.body().bytes());
        fos.close();
    }

    @Override
    public String getSplineChartOfInvokeTimes(String userPin, Long apiId, LocalDate date) throws IOException {
        date = date == null ? LocalDate.now().minusDays(1) : date;
        getInvokeInfo(userPin, apiId, date).forEach((k, v) -> System.out.println(k + " : " + v));
        Map<String, Integer> datas = fakeSplineData();    // to be replaced
        if (datas.size() == 0) {
            return "";
        }
        ClassPathResource resource = new ClassPathResource("template/highcharts/spline_template.json");
        String content = join(Files.readAllLines(resource.getFile().toPath()), System.lineSeparator());
        List<String> keys = new ArrayList<>();
        List<Integer> values = new ArrayList<>();
        datas.forEach((k, v) -> {
            keys.add(k);
            values.add(v);
        });
        OptionalInt maxOption = datas.entrySet().stream().mapToInt(Map.Entry::getValue).max();
        // Y 轴步长
        int tickInterval = maxOption.orElse(4) / 4.0 > 1.0 ? (int) Math.ceil(maxOption.orElse(4) / 4.0) : 1;
        String payload = content
                .replace("TICK_INTERVAL", String.valueOf(tickInterval))
                .replace("CATEGORIES", JSON.toJSONString(keys))
                .replace("DATA_ARRAY", JSON.toJSONString(values));
        String fileName = String.format("spline_%s_%d_%s", userPin, apiId, date.format(DateTimeFormatter.BASIC_ISO_DATE));
        generateImage(Constants.HIGHCHARTS_EXPORT_SERVER_URL + IMAGE_TYPE, payload, fileName);
        String fileJson = storagePicture.upload(Files.readAllBytes(Paths.get(EXPORT_DIRECTORY + fileName)));
        JSONArray jsonArray = JSONArray.parseArray(fileJson);
        JSONObject jsonObj = (JSONObject) jsonArray.get(0);
        deleteTmpFileAsync(EXPORT_DIRECTORY + fileName);
        return (String) jsonObj.get("msg");
    }

    @Override
    public String getRingChartOfInvokeErrors(String userPin, Long apiId, LocalDate date) throws IOException {
        date = date == null ? LocalDate.now().minusDays(1) : date;
        getErrorDistribute(userPin, apiId, date).forEach((k, v) -> System.out.println(k + " : " + v));  // to be replaced
        Map<String, Double> datas = fakeRingData();  // getErrorDistribute(userPin, apiId, date);
        if (datas.size() == 0) {
            return "";
        }
        ClassPathResource resource = new ClassPathResource("template/highcharts/ring_template.json");
        List<String> list = Files.readAllLines(resource.getFile().toPath());
        List<String> colorSchema = null;
        if (datas.size() == 1 && datas.containsKey(GwErrorCodeEnum.SUCCESS.getName())) {
            colorSchema = ColorSchemaEnum.SUCCESS.getColors();
        } else if (datas.size() < 4) {
            colorSchema = ColorSchemaEnum.getColorsByNum(datas.size());
        } else {
            colorSchema = ColorSchemaEnum.FOUR.getColors();
        }
        List<String> entries = datas.entrySet()
                                    .stream()
                                    .map(e -> {
                                        Map<String, Object> m = new HashMap<>();
                                        m.put("name", e.getKey());
                                        m.put("y", e.getValue());
                                        return m;
                                    })
                                    .map(JSON::toJSONString)
                                    .collect(toList());
        String content = join(list, System.lineSeparator());
        String payload = content.replace("COLOR_ARRAY", JSON.toJSONString(colorSchema))
                                .replace("DATA_ARRAY", join(entries));
        String fileName = String.format("ring_%s_%d_%s", userPin, apiId, date.format(DateTimeFormatter.BASIC_ISO_DATE));
        generateImage(Constants.HIGHCHARTS_EXPORT_SERVER_URL + IMAGE_TYPE, payload, fileName);
        String fileJson = storagePicture.upload(Files.readAllBytes(Paths.get(EXPORT_DIRECTORY + fileName)));
        JSONArray jsonArray = JSONArray.parseArray(fileJson);
        JSONObject jsonObj = (JSONObject) jsonArray.get(0);
        deleteTmpFileAsync(EXPORT_DIRECTORY + fileName);
        return (String) jsonObj.get("msg");
    }

    private void deleteTmpFileAsync(String filePath) {
        CompletableFuture.runAsync(() -> {
            File file = new File(filePath);
            if (file.delete()) {
                logger.debug("file {} successfully deleted.", filePath);
            } else {
                logger.error("failed to delete file {}, please delete it manually.", filePath);
            }
        });
    }

    private Map<String, Integer> fakeSplineData() {
        Map<String, Integer> datas = new LinkedHashMap<>();
        IntStream.rangeClosed(0, 23)
                .forEach(i -> {
                    datas.put(String.valueOf(i), (int) Math.ceil(10 * Math.random()));
                });
        return datas;
    }

    private Map<String, Double> fakeRingData() {
        Map<String, Double> datas = new HashMap<>();
//        datas.put("正常", 20.0);
        datas.put("接口欠费", 45.0);
        datas.put("网关超时", 30.0);
        datas.put("商家接口异常", 15.0);
        datas.put("未知异常", 10.0);
        return datas;
    }

    private Map<String, Integer> getInvokeInfo(String userPin, Long apiId, LocalDate date) {
        Map<String, String> raw = gwCollectMgtService.getResultHourCountByUser(userPin,
                                        String.valueOf(apiId), date.format(DateTimeFormatter.BASIC_ISO_DATE));
        if (raw.isEmpty()) {
            return new LinkedHashMap<>();
        }
        Map<String, Integer> datas = new LinkedHashMap<>();
        raw.forEach((k, v) -> {
            datas.put(k, Integer.valueOf(v));
        });
        return datas;
    }

    /**
     *  1、如果接口调用全部成功，显示“正常”
     *  2、如果接口报错类型不超过3种，显示全部的报错类型及所占的比例
     *  3、如果接口报错类型超过3种，显示占比最高的前三种报错类型及其所占的比例，其它类型所占的比例相加并显示为“其它”
     * @param userPin
     * @param apiId
     * @param date
     * @return
     */
    private Map<String, Double> getErrorDistribute(String userPin, Long apiId, LocalDate date) {
        Map<String, String> raw = gwCollectMgtService.getResultCodeRateByUser(userPin,
                                        String.valueOf(apiId), date.format(DateTimeFormatter.BASIC_ISO_DATE));
        if (raw.isEmpty()) {
            return new HashMap<>();
        }
        Map<String, Double> intermediate =
                raw.entrySet()
                .stream()
                .sorted(Map.Entry.comparingByValue(Comparator.comparingDouble(Double::valueOf)))
                .collect(Collectors.toMap(e -> GwErrorCodeEnum.getNameByCode(e.getKey()), e -> Double.valueOf(e.getValue()), (k1, k2) -> k1, LinkedHashMap::new));

        if (intermediate.size() < 4) {
            return intermediate;
        }
        Double leftShare = intermediate.entrySet()
                            .stream()
                            .limit(intermediate.size() - 3)
                            .map(Map.Entry::getValue)
                            .reduce(0.0, (v1, v2) -> v1 + v2);

        Map<String, Double> datas = intermediate.entrySet()
                        .stream()
                        .skip(intermediate.size() - 3)
                        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

        datas.put("其它", leftShare);
        return datas;
    }

    enum ColorSchemaEnum {
        SUCCESS(0, singletonList("#8ed97e")),   // 接口调用未出错
        ONE(1, singletonList("#ffa1a5")),    // 只有一种错误
        TWO(2, asList("#ffa1a5", "#7fc3ff")),    // 有两种错误
        THREE(3, asList("#ffa1a5", "#7fc3ff", "#fed97e")),    // 有三种错误
        FOUR(4, asList("#ffa1a5", "#7fc3ff", "#fed97e", "#cccccc"));    // 四种错误

        private final int num;
        private final List<String> colors;

        ColorSchemaEnum(int num, List<String> colors) {
            this.num = num;
            this.colors = colors;
        }

        public int getNum() {
            return num;
        }

        public List<String> getColors() {
            return colors;
        }

        public static List<String> getColorsByNum(int num) {
            return Arrays.stream(ColorSchemaEnum.values())
                    .filter(en -> en.num == num)
                    .findAny()
                    .map(en -> en.colors)
                    .orElse(new ArrayList<>());
        }
    }

}
