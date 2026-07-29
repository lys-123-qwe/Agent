package com.example.youkeda_project.service;

import com.example.youkeda_project.config.ApiConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

@Service
public class TranslationService {

    private static final Logger log = LoggerFactory.getLogger(TranslationService.class);
    private static final String API_URL = "https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions";

    private final HttpClient httpClient;
    private final ApiConfig apiConfig;

    public TranslationService(ApiConfig apiConfig) {
        this.apiConfig = apiConfig;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .build();
    }

    public String translate(String text, String targetLang, String mode, String dialect) {
        String apiKey = apiConfig.getImageApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            return "错误: 未配置阿里云 API Key";
        }

        String systemPrompt = buildPrompt(mode != null ? mode : "general", targetLang, dialect);

        try {
            String body = "{\"model\":\"qwen-turbo\",\"messages\":["
                + "{\"role\":\"system\",\"content\":" + jsonEscape(systemPrompt) + "},"
                + "{\"role\":\"user\",\"content\":" + jsonEscape(text) + "}"
                + "]}";

            log.info("翻译请求: mode={}, target={}, dialect={}, textLen={}", mode, targetLang, dialect, text.length());

            var hreq = HttpRequest.newBuilder()
                    .uri(URI.create(API_URL))
                    .header("Content-Type", "application/json; charset=UTF-8")
                    .header("Authorization", "Bearer " + apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                    .timeout(Duration.ofSeconds(30))
                    .build();

            HttpResponse<String> hresp = httpClient.send(hreq, HttpResponse.BodyHandlers.ofString());
            String respBody = hresp.body();
            log.info("翻译响应: {}", respBody.length() > 200 ? respBody.substring(0, 200) + "..." : respBody);

            var mapper = new ObjectMapper();
            var root = mapper.readTree(respBody);
            var choice = root.path("choices").get(0);
            if (choice == null) return "翻译服务暂不可用";

            String result = choice.path("message").path("content").asText("");
            if (result.isBlank()) return "翻译结果为空";
            return result;
        } catch (Exception e) {
            log.error("翻译失败", e);
            return "翻译失败: " + e.getMessage();
        }
    }

    private String buildPrompt(String mode, String targetLang, String dialect) {
        return switch (mode) {
            case "classical" ->
                """
                你是一个文言文与白话文互译专家.规则:
                1. 如果输入是文言文/古文/诗词,翻译成现代白话文,保留原意和美感
                2. 如果输入是现代白话文,翻译成文言文/古文,使用典雅的文言表达
                3. 如果输入包含古今混合,按主要倾向判断
                只输出翻译结果,不要添加任何解释,备注或额外内容.""";

            case "dialect" -> buildDialectPrompt(dialect);

            case "tech" ->
                """
                你是一个计算机/信息技术专业翻译专家.规则:
                1. 将输入的计算机技术文本准确翻译
                2. 修正机器翻译常见的专业术语错误
                3. 术语对照表(严格遵守):
                   - API → API(不译)
                   - framework → 框架
                   - protocol → 协议
                   - interface → 接口
                   - implementation → 实现
                   - deployment → 部署
                   - dependency → 依赖
                   - repository → 仓库
                   - middleware → 中间件
                   - callback → 回调
                   - thread → 线程
                   - asynchronous → 异步
                   - buffer → 缓冲区
                   - cache → 缓存
                   - concurrency → 并发
                   - encryption → 加密
                   - authentication → 认证
                   - authorization → 授权
                   - latency → 延迟
                   - throughput → 吞吐量
                   - scalability → 可扩展性
                   - IDE → IDE(不译)
                   - SDK → SDK(不译)
                   - CLI → 命令行
                   - GUI → 图形界面
                4. 保持代码,命令,路径等原样不译
                5. 只输出翻译结果,不要解释""";

            default -> {
                String targetName = switch (targetLang != null ? targetLang : "") {
                    case "en" -> "英语";
                    case "ja" -> "日语";
                    case "ko" -> "韩语";
                    case "fr" -> "法语";
                    case "zh" -> "中文";
                    default -> targetLang != null ? targetLang : "英语";
                };
                yield "你是一个翻译专家.自动检测输入文本的语种,将其翻译为" + targetName
                    + ".保持专有名词,人名,地名,品牌名不译.只输出翻译结果,不要添加任何解释或额外内容.";
            }
        };
    }

    private String buildDialectPrompt(String dialect) {
        if (dialect == null || dialect.isBlank()) {
            return """
                你是一个方言转换专家.规则:
                1. 自动判断输入文本的语言类型(标准中文/方言/混合)
                2. 如果输入是标准中文,将其转换为对应的方言(四川话,东北话,粤语,上海话,河南话,陕西话等)
                3. 如果输入是方言,将其转换为标准中文
                4. 如果输入包含多种方言或无法判断,默认转换为四川话
                转换要求:
                - 保留原意,使用该方言典型词汇,语气词,语法结构
                - 不要过度夸张或使用刻板印象表达
                - 保持自然流畅,让人感觉是当地人说话
                只输出转换结果,不要添加任何解释,备注或额外内容.""";
        }

        return """
            你是一个方言转换专家.将输入文本转换为""" + dialect + """
            .规则:
            1. 如果输入是标准中文,转换为""" + dialect + """
            2. 如果输入已经是""" + dialect + ",转换为标准中文" + """
            3. 如果输入是其他方言,先转换为标准中文再转目标方言
            转换要求:
            - 使用地道的""" + dialect + "词汇,语气词,表达方式" + """
            - 不要过度夸张或使用刻板印象
            - 保持自然流畅
            """ + getDialectHint(dialect) + """
            只输出转换结果,不要添加任何解释,备注或额外内容.""";
    }

    private String getDialectHint(String dialect) {
        return switch (dialect) {
            case "四川话", "四川" ->
                """
                提示:四川话特色包括--"啥子"(什么),"咋个"(怎么),"巴适"(舒服/好),"安逸"(舒适),
                "要得"(行/可以),"晓得"(知道),"莫得"(没有),"雄起"(加油),"瓜娃子"(傻瓜/亲昵),
                "摆龙门阵"(聊天),语气词"哦/嘛/噻/咯"结尾.""";

            case "东北话", "东北" ->
                """
                提示:东北话特色包括--"啥"(什么),"咋"(怎么),"整"(做/搞/弄的万能动词),
                "老好了"(非常好),"贼"(很/非常,如"贼好吃"),"嘎哈"(干嘛),"得瑟"(炫耀),
                "拉倒"(算了),"稀罕"(喜欢),"磨叽"(啰嗦),"怼"(顶撞/碰撞),语气词"呗/呢/哈".""";

            case "粤语", "广东话", "广东" ->
                """
                提示:粤语特色包括--"唔"(不),"系"(是),"嘅"(的),"咗"(了),"哋"(们),
                "冇"(没有),"同"(和),"睇"(看),"讲"(说),"食"(吃),"行"(走),
                "点解"(为什么),"几时"(什么时候),"边个"(谁),"乜嘢"(什么),
                "唔该"(谢谢/劳驾),"多谢"(谢谢),语气词"啦/咯/啫/咋".""";

            case "上海话", "上海", "沪语" ->
                """
                提示:上海话特色包括--"侬"(你),"伊"(他/她),"阿拉"(我们),"伊拉"(他们),
                "啥"(什么),"哪能"(怎么),"伐"(吗/不,疑问/否定),"邪气"(非常),
                "老"(很,如"老灵"),"灵光"(好/棒),"白相"(玩),"辰光"(时间),
                "屋里"(家),"今朝"(今天),"明朝"(明天),语气词"伐/呀/呢".""";

            case "河南话", "河南" ->
                """
                提示:河南话特色包括--"中"(行/可以/好),"得劲儿"(舒服),"不沾弦"(不行/不靠谱),
                "怼"(万能动词),"弄啥嘞"(干什么),"恁"(那么/你),"啥"(什么),
                "咋"(怎么),"得"(dei,必须),"镇"(这么,如"镇大"),
                "清倒起"(反过来),语气词"嘞/哩/呗".""";

            case "陕西话", "陕西" ->
                 """
                 提示:陕西话特色包括--"饿"(我),"你"(ni,读三声),"啥"(什么),
                 "咋"(怎么),"谝"(聊天/吹牛),"嫽咋咧"(好极了),"嘹咋咧"(美得很),
                 "瓷"(笨/迟钝),"倭也"(舒服/好),"毕了"(完了/结束了),
                 "克里马擦"(快点/麻利),语气词"咧/么/嘛".""";

            case "湖南话", "湖南", "湘语" ->
                """
                提示:湖南话特色包括--"咯"(语气词),"不咯"(吗),"何解"(为什么),
                "哦该"(为什么/怎么),"咯里"(这里),"那里"(那里),"细伢子"(小孩),
                "堂客"(老婆),"呷饭"(吃饭),"冇得"(没有),"硬是"(实在是/硬是),
                "要得"(可以/好),语气词"咯/咧/啵".""";

            case "闽南语", "闽南", "福建" ->
                """
                提示:闽南语特色包括--"我"(wa/挖),"你"(li/力),"伊"(yi/一,他/她),
                "阮"(gun,我们),"啥物"(什么),"为什么"(按怎/án-chóaⁿ),
                "真"(非常),"水"(sui,漂亮),"甲"(gah,和/跟/得),
                "呷"(吃/喝),"走"(跑),"行"(走),"无"(没有),
                "有"(wu,有),语气词"啦/喔/啊".""";

            case "客家话", "客家" ->
                """
                提示:客家话特色包括--"𠊎"(ngai,我),"你"(ng/嗯),"佢"(gi,他/她),
                "𠊎等"(我们),"么个"(什么),"做么个"(为什么),"好得"(幸好),
                "满人"(谁),"倔"(giet,很/非常),"食"(吃),"着"(穿),
                "行"(走),"无"(mau,没有),"系"(he,是),语气词"哦/啊/呢".""";

            case "天津话", "天津" ->
                """
                提示:天津话特色包括--"嘛"(什么),"干嘛"(干什么),"倍儿"(非常,很),
                "齁"(hou,太/过于),"了"(le读lou),"您了"(您,尊称),
                "大梨"(吹牛),"白话"(baí huo,闲聊),"哏"(有趣/幽默),
                "归齐"(归根到底),"走鸡"(出岔子/跑偏),语气词"嘛/呗/呢".""";

            case "山东话", "山东" ->
                """
                提示:山东话特色包括--"俺"(我/我们),"恁"(你们/那么),"啥"(什么),
                "咋"(怎么),"木有"(没有),"可"(很/非常,如"可好了"),
                "溜"(滑/顺),"恣"(高兴/舒服),"磕碜"(难看/丢人),
                "拉呱"(聊天),"揍啥"(做什么),语气词"昂/哈/呗/咧".""";

            case "重庆话", "重庆" ->
                """
                提示:重庆话属于西南官话,特色包括--"啥子"(什么),"咋个"(怎么),"要得"(行),
                "安逸"(舒适),"巴适"(好/棒),"雄起"(加油),"婆娘"(老婆),
                "老汉"(爸爸),"娃儿"(孩子),"堂客"(老婆),"耿直"(豪爽),
                "不存在"(没关系),"斗是"(就是),语气词"噻/咯/嘛/哦".""";

            case "云南话", "云南" ->
                """
                提示:云南话特色包括--"给"(gé,是否),"咋个"(怎么),"哪样"(什么),
                "整"(做),"板扎"(很棒/厉害),"好在"(舒服/安逸),
                "鬼火录"(很生气),"太仙了"(太神奇了),"克"(去),
                "抬"(端/拿),"咋说"(怎么说),语气词"噶/嘛/咯/啰".""";

            case "贵州话", "贵州" ->
                """
                提示:贵州话特色包括--"哪样"(什么),"咋个"(怎么),"搞哪样"(做什么),
                "安逸"(舒服),"归一"(完了/结束),"毛"(发火/生气),
                "鬼扯"(胡说),"弯酸"(挑剔/刁难),"扎板"(稳重/可靠),
                "莽"(mang,笨),"克"(去),语气词"咯/嘛/喔/噻".""";

            default ->
                """
                提示:使用""" + dialect + "的典型词汇,语气词,语法结构进行转换,保持自然地道.";
        };
    }

    private String jsonEscape(String s) {
        return "\"" + s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t") + "\"";
    }
}
