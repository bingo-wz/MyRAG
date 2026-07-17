package com.wangzhi.knowledgebase.config;

import com.wangzhi.knowledgebase.domain.QuestionLog;
import com.wangzhi.knowledgebase.dto.KnowledgeDtos.CreateRequest;
import com.wangzhi.knowledgebase.dto.KnowledgeDtos.ReviewRequest;
import com.wangzhi.knowledgebase.dto.KnowledgeDtos.View;
import com.wangzhi.knowledgebase.repository.QuestionLogRepository;
import com.wangzhi.knowledgebase.service.KnowledgeService;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Random;

@Component
@ConditionalOnProperty(name = "app.demo-data", havingValue = "true", matchIfMissing = true)
public class DemoDataInitializer implements ApplicationRunner {

    private final KnowledgeService knowledgeService;
    private final QuestionLogRepository logRepository;

    public DemoDataInitializer(KnowledgeService knowledgeService, QuestionLogRepository logRepository) {
        this.knowledgeService = knowledgeService;
        this.logRepository = logRepository;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (knowledgeService.search(null, null, null, 0, 1).totalElements() > 0) {
            return;
        }
        List<View> approved = List.of(
                create("手机退换货服务规则", "用户签收商品之日起 7 日内，商品保持完好且配件齐全，可申请无理由退货。质量问题支持 15 日内换货。激活后影响二次销售的商品不适用无理由退货。申请路径：商城 App - 我的 - 订单 - 申请售后。", "售后服务", "服务政策中心", "退货,换货,售后"),
                create("官方保修政策", "手机主机自购买之日起享受一年有限保修。保修期内，经官方授权服务中心检测属于非人为性能故障的，可免费维修。进液、摔落、私自拆机等人为损坏不在标准保修范围内。用户可凭电子发票或有效购机凭证申请服务。", "售后服务", "服务政策中心", "保修,维修,凭证"),
                create("会员积分使用说明", "会员积分可在积分商城兑换权益，也可在部分订单结算时抵扣。100 积分可抵扣 1 元，每笔订单最高抵扣商品金额的 10%。积分不可转赠或提现，取消订单后已使用积分将原路退回。", "会员权益", "会员运营平台", "积分,抵扣,会员"),
                create("优惠券使用规则", "优惠券需在有效期内使用，过期自动失效。满减券按商品实付金额判断门槛，同一订单默认仅可使用一张平台券；商品券与平台券能否叠加以结算页展示为准。发生整单退款时优惠券按规则退回，过期券不再返还。", "营销活动", "营销管理系统", "优惠券,满减,退款"),
                create("订单配送时效", "现货订单通常在付款后 24 小时内出库。深圳、广州等核心城市预计 1 至 2 天送达，偏远地区预计 3 至 5 天。预售商品以商品详情页承诺时间为准，可在订单详情中查看物流轨迹。", "订单物流", "履约中心", "配送,物流,时效"),
                create("以旧换新操作指南", "用户可在商城首页进入以旧换新，选择旧机型号并完成在线估价。下单新机时可选择上门回收或到店回收。最终回收价以工程师验机结果为准，如与预估价不一致，用户可选择确认或取消回收。", "购买指南", "回收业务平台", "以旧换新,估价,回收"),
                create("电池健康与保养建议", "建议使用原装或符合安全认证的充电设备，避免设备长期处于高温环境。系统会根据使用习惯优化充电速度。电池属于消耗品，容量会随使用时间自然下降；如续航明显异常，可前往服务 App 进行智能检测。", "产品使用", "产品技术中心", "电池,充电,续航"),
                create("账号注销与数据处理", "用户可在账号中心提交注销申请。系统完成身份校验且确认无未完成订单、售后或余额后进入注销流程。注销完成后，个人数据将依据法律法规与隐私政策进行删除或匿名化处理，部分交易记录依法保留。", "账号安全", "隐私合规中心", "注销,隐私,数据"),
                create("发票开具说明", "订单完成后可在订单详情申请电子普通发票。发票抬头支持个人或企业，企业抬头需填写税号。发票内容与实际购买商品一致，电子发票开具后将发送至账号绑定邮箱，也可在订单详情下载。", "订单服务", "财务结算中心", "发票,税号,订单"),
                create("服务网点预约流程", "打开服务 App，进入服务门店，选择附近的官方授权服务中心和可预约时段。提交设备型号、故障描述及联系方式后即完成预约。到店时请携带设备和购机凭证，提前备份个人数据。", "售后服务", "服务运营平台", "网点,预约,维修")
        );
        approved.forEach(view -> {
            knowledgeService.submit(view.id());
            knowledgeService.review(view.id(), new ReviewRequest(true, "王敏", "内容与现行政策一致"));
        });

        View pending = create("暑期学生优惠活动说明", "学生认证用户购买指定机型可享教育优惠，具体机型和优惠额度以活动页面为准。", "营销活动", "活动运营", "学生优惠,活动");
        knowledgeService.submit(pending.id());
        create("门店体验机管理规范", "体验机每日开店前完成外观、网络和演示账号检查，闭店后统一充电并登记异常。", "门店运营", "零售运营", "门店,体验机");
        View rejected = create("旧版碎屏服务说明", "购买指定保障服务后可申请一次屏幕维修。", "售后服务", "历史资料", "碎屏,保障");
        knowledgeService.submit(rejected.id());
        knowledgeService.review(rejected.id(), new ReviewRequest(false, "李航", "缺少适用机型和有效期，请补充"));

        seedQuestionLogs();
    }

    private View create(String title, String content, String domain, String source, String tags) {
        return knowledgeService.create(new CreateRequest(title, content, domain, source, tags, "知识运营"));
    }

    private void seedQuestionLogs() {
        String[] questions = {
                "手机买了六天可以退吗", "保修期多久", "积分可以抵多少钱", "优惠券能叠加吗",
                "深圳多久能送到", "怎么参加以旧换新", "电池掉得快怎么办", "怎么注销账号",
                "电子发票在哪里下载", "维修需要预约吗", "学生优惠支持哪些机型", "碎屏险能修几次"
        };
        Random random = new Random(42);
        for (int i = 0; i < 78; i++) {
            QuestionLog log = new QuestionLog();
            log.setTraceId("DEMO%012d".formatted(i));
            log.setAskedBy("demo-user");
            log.setDomain("演示数据");
            log.setQuestion(questions[i % questions.length]);
            log.setAnswer(i % 11 == 0 ? "当前知识不足，暂时无法给出可靠结论。" : "已根据知识库返回对应规则与办理路径。详情请核对引用来源。");
            log.setConfidence(i % 11 == 0 ? 0.42 : 0.71 + random.nextDouble() * 0.24);
            log.setLatencyMs(280 + random.nextInt(920));
            boolean accepted = i % 7 != 0;
            log.setAccepted(accepted);
            log.setBadCase(!accepted || log.getConfidence() < 0.55);
            log.setBadReason(accepted ? null : (i % 2 == 0 ? "回答缺少适用范围" : "召回了过期知识"));
            log.setSourceSnapshot("[]");
            log.setModelName("demo-extractive");
            log.setPromptVersion("demo-v1");
            log.setCreatedAt(LocalDateTime.now().minusDays(6 - i % 7).minusMinutes(i * 13L));
            logRepository.save(log);
        }
    }
}
