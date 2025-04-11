 changed from chinese to english

# Changelog

## V 1.8.0

*2021-01-10* Minor improvements...

## V 1.7.0

*2020-02-15* Added support for Docker deployment, after-sales management, notification management, and seven-day database backup.

#### Bug Fixes

  * `Mini Mall`: Fixed horizontal privilege escalation vulnerability in some backend APIs.
  * `Mini Mall`: Verification code could be sent without timeout expiration.
  * `Mini Mall`: Displayed admin reply to comments (#340 by sunyinggang).
  * `Admin Panel`: Admin replies to comments (#340 by sunyinggang).
  * `Lite Mall`: Incorrect return when adding shipping address (#320 by kevinleeex).

#### Optimizations

  * `Admin Panel`: Topics now support sorting and batch deletion.
  * `Core System`: Added indexes to four tables (#328 #330 #332 #334 by wtune).

#### New Features

  * `Core System`: Added Docker deployment support (#321 by yuana1).
  * `Core System`: Automatic 7-day data backup to the backup folder.
  * `Admin Panel`: Notification center and notification management.
  * `Admin Panel`: Added copyright content on login page.
  * `Admin Panel`: After-sales management.
  * `Mini Mall`: After-sales list, details, and application process.
  * `Lite Mall`: Implemented account registration (#324 by yelongbao).

## V 1.6.1

*2020-01-01*

#### Bug Fixes

  * `Core System`: Removed unnecessary Bean annotations that caused unwanted instantiation.

## V 1.6.0

*2019-12-31* Enhanced group-buy implementation and removed the WeChat template feature.

#### Bug Fixes

  * `Admin Panel`: Fixed image overflow when viewing product details (#305 by zaoangod).

#### Optimizations

  * `Mini Mall`: Refactored group-buy feature.
  * `Mini Mall`: Removed WeChat template feature.
  * `Admin Panel`: Improved query speed (# by).
  * `Admin Panel`: Updated vue-element-admin framework to version 4.2.1.

#### New Features

  * `Mini Mall`: Replaced icons with Vant icons where possible.
  * `Mini Mall`: Added product link to homepage banner (#299 by staneychan).
  * `Mini Mall`: Configured email notifications to use SSL (#307 by jessonxiang).

## V 1.5.0

*2019-11-15* Continued to optimize Lite Mall module and introduced recommended project Flutter_Mall.

#### Bug Fixes

  * `Mini Mall`: Fixed coupon binding issue (#157 by @beaver383).
  * `Mini Mall`: Fixed incorrect display of comment list.
  * `Lite Mall`: Corrected cancel order API (#256 by @1037621594).

#### Optimizations

  * `Mini Mall`: Implemented delayed queue for payment timeout and order cancellation (#275 by @alexzhu0592).
  * `Mini Mall`: Optional configuration for share button (#239 by @galenzhao).

#### New Features

  * `Core System`: Added Alibaba Cloud SMS support.
  * `Lite Mall`: Integrated WeChat Pay H5 payment (#291 by @beaver383).
  * `Mini Mall`: Group-buy auto-cancellation on expiry (#284 by @beaver383).
  * `Admin Panel`: Added print feature to order details (#274 by @fanchenggang).
  * README document recommends project Flutter_Mall.

## V 1.4.0

*2019-05-16* Added support for Lite Mall mobile version.

#### Bug Fixes

  * `Mini Mall`: Item quantity in cart and orders must be positive integers.
  * `Mini Mall`: Fixed verification failure in WeChat payment callback notification.
  * `Mini Mall`: Unified query by `userId` and `id` for shipping address.
  * `Admin Panel`: Prevented admin from deleting their own account.

#### Optimizations

  * `Documentation`: Added API documentation support.
  * `Core System`: Updated mybatis-generator-plugin to version 1.3.2.
  * `Admin Panel`: Disabled password modification for admins through edit API.

#### New Features

  * `Mini Mall`: Help center page.
  * `Mini Mall`: Backend login with JWT authentication (#167 by @Bigger-Ma).
  * `Lite Mall`: Completed basic structure (#157 by @pkwenda).
  * `Admin Panel`: Operation log management.

## V 1.3.0

*2019-03-11* Added configuration management.

  * `Admin Panel`: Product category and administrative region pages now displayed in a tree structure.
  * `Admin Panel`: Removed internationalization and themes.
  * `Admin Panel`: Added configuration management.

  **Note**: Although the order timeout can be configured, a delay may occur due to the current polling method. Future updates will address this (e.g., using Redis).

## V 1.2.0

*2019-03-03* Added permission management.

  * `Admin Panel`: Added permission management.
  * `Mini Mall`: Switched from programmatic to annotation-based transaction management.
  * `Mini Mall`: Enabled multi-threaded database queries.

## V 1.1.0

*2018-12-23* Added coupon support.

  * `Admin Panel`: Coupon management.
  * `Admin Panel`: Moved scheduled tasks to `job` sub-package for easier migration.
  * `Mini Mall`: Displayed coupon list and personal coupons.
  * `Core System`: Adjusted KuaiDiNiao API.

## V 1.0.0

*2018-12-03* Documentation improvements.

## V 1.0.0.rc1

*2018-11-30* Integrated WeChat refund API.

  * `Admin Panel`: WeChat refund API integration.
  * `Admin Panel`: Removed magic numbers from error codes.
  * `Admin Panel`: Prohibited password change for super admins.

## V 1.0.0.rc0

*2018-11-23* Cleaned up code and updated admin panel framework.

  * `Admin Panel`: Updated to vue-element-admin version 3.9.3.
  * `Admin Panel`: Custom Mapper for stock adjustments.
  * `Admin Panel`: Enabled product replies.
  * `Mini Mall`: Enabled product replies.

## V 0.10.2

*2018-11-08* Fixed minor issues.

  * `Admin Panel`: Rich text editing adjustments to fix alignment on mini-program.
  * `Mini Mall`: Added group-buy section.
  * `Mini Mall`: Disabled default built-in cache.

## V 0.10.1

*2018-11-07* Fixed minor issues.

## V 0.10.0

*2018-10-26* Fixed multiple small issues.

  * `Admin Panel`: POST parameter validation.
  * `Admin Panel`: Removed optimistic lock for all but the order table.

## V 0.9.0

*2018-09-14* Added group-buy support and second Mini Mall `renard-wx`.

  * `Mini Mall`: Group-buy support.
  * `Mini Mall`: Open-sourced `renard-wx`.
  * `Mini Mall`: Added feedback component.
  * `Admin Panel`: Updated with optimistic locking.
  * `Admin Panel`: Upgraded to Spring Boot 2.x.

## V 0.8.0

*2018-07-30* Removed `os` module and improved mini-program.

  * `Mini Mall`: Added product sharing and logistics tracking.
  * `Mini Mall`: Style improvements, thanks to [usgeek](https://github.com/linlinjava/litemall/pull/31).
  * `Mini Mall`: Added customer service, about page, and phone binding.
  * `Mini Mall`: Added SMS verification for registration and password recovery.
  * `Core System`: Supported Alibaba Cloud storage, thanks to [usgeek](https://github.com/linlinjava/litemall/pull/31).
  * `Project`: Removed `os` module; functionality moved to `wx-api` and `admin-api`.
  * `Project`: Shifted default setup to single-service configuration.

## V 0.7.0

*2018-07-16* Simplified database and added SMS/email notifications and Tencent object storage.

  * `Admin Panel`: Query results default to sorting by creation time.
  * `Admin Panel`: Enabled product listing and editing.
  * `Core System`: Added Tencent SMS/email notifications, thanks to [Menethil](https://github.com/linlinjava/litemall/pull/23).
  * `Project`: Simplified database with corresponding adjustments in Mini Mall and Admin Panel code.

## V 0.6.0

*2018-06-30* Added product listing and statistics features.

  * `Mini Mall`: Adjusted for WeChat API changes.
  * `Admin Panel`: Basic statistics and product listing support.
  * `Project`: Enabled Docker deployment.

## V 0.5.0

*2018-05-11* Supported WeChat payment and fixed mini-program bugs.

  * `Mini Mall`: Adjusted for WeChat API changes.
  * `Mini Mall`: Fixed checkout issues and address display bugs.
  * `Admin Panel`: WeChat payment support.
  * `Core System`: Added auto-increment for `litemall_collect` table.

## V 0.4.0

*2018-04-21* Reorganized project structure, added two new modules.

## V 0.3.0

*2018-04-07* Changed business logic