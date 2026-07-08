package shop.payment.service;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import shop.payment.model.Subscription;
import shop.payment.repository.SubscriptionRepository;

import java.time.LocalDateTime;
import java.util.List;

// local emulator of LiqPay recurring-charge callbacks: LiqPay cannot reach localhost,
// so on the monolith's schedule we create the orders the callbacks would have created.
// Spring's default scheduler is single-threaded — the tasks never overlap.
@Service
@RequiredArgsConstructor
public class SubscriptionScheduleService {

    private static final Logger logger = LoggerFactory.getLogger(SubscriptionScheduleService.class);

    private final SubscriptionRepository subscriptionRepository;
    private final SubscriptionService subscriptionService;

    @Scheduled(cron = "${task.cron.day}")
    public void chargeDaily() {
        charge("day");
    }

    @Scheduled(cron = "${task.cron.week}")
    public void chargeWeekly() {
        charge("week");
    }

    @Scheduled(cron = "${task.cron.month}")
    public void chargeMonthly() {
        charge("month");
    }

    @Scheduled(cron = "${task.cron.year}")
    public void chargeYearly() {
        charge("year");
    }

    private void charge(String periodicity) {
        List<Subscription> due =
                subscriptionRepository.findByPeriodicityAndStartDateBefore(periodicity, LocalDateTime.now());
        logger.info("Scheduled '{}' run: {} subscription(s) due", periodicity, due.size());

        for (Subscription subscription : due) {
            try {
                subscriptionService.createOrderFromSubscription(subscription);
            } catch (Exception e) {
                // one broken subscription must not stop the rest of the run
                logger.error("Recurring order failed for subscription {}", subscription.getId(), e);
            }
        }
    }
}
