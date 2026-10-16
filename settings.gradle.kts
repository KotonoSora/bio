rootProject.name = "resume-builder-platform"

// Edge gateway (standalone build in gateway/)
include("gateway")

// Domain services — one Gradle build per service, database-per-service.
// No shared source tree between services (baseline 05-delivery-phasing-and-scaffold).
include("services:account-service")
include("services:profile-service")
include("services:link-service")
include("services:public-site-service")
include("services:analytics-service")
include("services:resume-service")
include("services:shop-service")
include("services:billing-service")
include("services:publish-service")
