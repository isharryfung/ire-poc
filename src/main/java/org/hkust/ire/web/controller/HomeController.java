package org.hkust.ire.web.controller;

import org.hkust.ire.db.persistence.service.monitoring.MetricsService;
import org.hkust.ire.db.persistence.service.review.ReviewQueueManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.HashMap;
import java.util.Map;

/**
 * Controller for the admin dashboard and home page (JSP views).
 *
 * @author ire-team
 * @since 1.0.0
 * @version 1.0.0
 */
@Controller
public class HomeController {

    private final Logger log = LoggerFactory.getLogger(this.getClass());

    @Autowired
    private MetricsService metricsService;

    @Autowired
    private ReviewQueueManager reviewQueueManager;

    /**
     * Returns the admin dashboard JSP view.
     *
     * @param model the Spring model
     * @return dashboard view name
     */
    @GetMapping("/")
    public String index(Model model) {
        log.debug("Serving index page");
        try {
            model.addAttribute("metrics", metricsService.collectMetrics());
            model.addAttribute("pendingReviews", reviewQueueManager.getPendingCount());
        } catch (Exception e) {
            log.error("Error loading dashboard data: {}", e.getMessage());
        }
        return "admin/dashboard";
    }

    /**
     * Returns the admin dashboard JSP view at /admin/dashboard.
     *
     * @param model the Spring model
     * @return dashboard view name
     */
    @GetMapping("/admin/dashboard")
    public String dashboard(Model model) {
        return index(model);
    }

    /**
     * Returns system health status as JSON.
     *
     * @return health status map
     */
    @GetMapping("/api/v1/health")
    @ResponseBody
    public Map<String, Object> health() {
        Map<String, Object> status = new HashMap<>();
        status.put("status", "UP");
        status.put("service", "IRE");
        status.put("timestamp", System.currentTimeMillis());
        return status;
    }
}
