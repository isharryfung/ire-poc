<!DOCTYPE html>
<html lang="en">
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>IRE Admin Dashboard - HKUST</title>
    <link rel="stylesheet" href="https://cdn.jsdelivr.net/npm/bootstrap@4.6.2/dist/css/bootstrap.min.css">
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/style.css">
</head>
<body>
<nav class="navbar navbar-dark bg-dark navbar-expand-lg">
    <a class="navbar-brand" href="#">IRE - Identity Resolution Engine</a>
    <div class="navbar-nav ml-auto">
        <a class="nav-link" href="${pageContext.request.contextPath}/review/queue">Review Queue</a>
        <a class="nav-link" href="${pageContext.request.contextPath}/admin/dashboard">Dashboard</a>
    </div>
</nav>

<div class="container-fluid mt-4">
    <div class="row">
        <div class="col-12">
            <h2>System Dashboard</h2>
            <hr>
        </div>
    </div>

    <div class="row">
        <div class="col-md-3">
            <div class="card text-white bg-primary mb-3">
                <div class="card-header">Total Identities</div>
                <div class="card-body">
                    <h4 class="card-title">${metrics['totalIdentities']}</h4>
                </div>
            </div>
        </div>
        <div class="col-md-3">
            <div class="card text-white bg-success mb-3">
                <div class="card-header">Active Identities</div>
                <div class="card-body">
                    <h4 class="card-title">${metrics['activeIdentities']}</h4>
                </div>
            </div>
        </div>
        <div class="col-md-3">
            <div class="card text-white bg-warning mb-3">
                <div class="card-header">Pending Reviews</div>
                <div class="card-body">
                    <h4 class="card-title">${pendingReviews}</h4>
                </div>
            </div>
        </div>
        <div class="col-md-3">
            <div class="card text-white bg-info mb-3">
                <div class="card-header">Avg Match Latency</div>
                <div class="card-body">
                    <h4 class="card-title">${metrics['avgMatchingLatencyMs']} ms</h4>
                </div>
            </div>
        </div>
    </div>

    <div class="row mt-3">
        <div class="col-12">
            <div class="card">
                <div class="card-header">Quick Actions</div>
                <div class="card-body">
                    <a href="${pageContext.request.contextPath}/review/queue" class="btn btn-warning mr-2">
                        Review Queue (${pendingReviews} pending)
                    </a>
                    <a href="${pageContext.request.contextPath}/api/v1/health" class="btn btn-info">
                        Health Check
                    </a>
                </div>
            </div>
        </div>
    </div>
</div>

<footer class="footer mt-5">
    <div class="container text-center text-muted py-3">
        IRE v1.0 - HKUST Identity Resolution Engine
    </div>
</footer>
<script src="https://code.jquery.com/jquery-3.5.1.min.js"></script>
<script src="https://cdn.jsdelivr.net/npm/bootstrap@4.6.2/dist/js/bootstrap.bundle.min.js"></script>
</body>
</html>
