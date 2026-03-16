package com.example.m1nd.service;

import com.example.m1nd.model.PaidService;
import com.example.m1nd.repository.PaidServiceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaidServiceService {

    public static final String BUSINESS_ASK_EXPERT_CODE = "business_ask_expert";

    private final PaidServiceRepository paidServiceRepository;

    public List<PaidService> findActiveServices() {
        return paidServiceRepository.findByActiveTrue();
    }

    /**
     * Возвращает цену услуги в минимальных единицах (XTR), создавая услугу с дефолтной ценой при отсутствии.
     */
    @Transactional
    public int getPriceUnitsOrDefault(String code, String titleFallback, String descFallback, String currency, int defaultPriceUnits) {
        PaidService service = paidServiceRepository.findByCode(code)
            .orElseGet(() -> {
                PaidService s = new PaidService();
                s.setCode(code);
                s.setTitle(titleFallback);
                s.setDescription(descFallback);
                s.setCurrency(currency);
                s.setPriceUnits(defaultPriceUnits);
                s.setActive(true);
                return paidServiceRepository.save(s);
            });

        if (service.getPriceUnits() == null || service.getPriceUnits() <= 0) {
            service.setPriceUnits(defaultPriceUnits);
            service.setCurrency(currency);
            service = paidServiceRepository.save(service);
        }

        return service.getPriceUnits();
    }

    /**
     * Устанавливает цену услуги в звёздах (stars), где 1 звезда = 100 XTR.
     */
    @Transactional
    public PaidService setPriceInStars(String code, String titleFallback, String descFallback, String currency, int stars) {
        int units = Math.max(stars, 0) * 100;
        PaidService service = paidServiceRepository.findByCode(code)
            .orElseGet(() -> {
                PaidService s = new PaidService();
                s.setCode(code);
                s.setTitle(titleFallback);
                s.setDescription(descFallback);
                s.setCurrency(currency);
                s.setActive(true);
                return s;
            });

        service.setPriceUnits(units);
        service.setCurrency(currency);

        return paidServiceRepository.save(service);
    }
}

