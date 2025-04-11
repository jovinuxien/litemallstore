 English Translation:

# Litemall

Another mini-mall system.

Litemall = Spring Boot backend + Vue admin frontend + WeChat mini-program user frontend + Vue mobile user frontend

* [Documentation](https://linlinjava.gitbook.io/litemall)
* [Contribute](https://linlinjava.gitbook.io/litemall/contribute)
* [FAQ](https://linlinjava.gitbook.io/litemall/faq)
* [API](https://linlinjava.gitbook.io/litemall/api)

## Project Code

* [Gitee](https://gitee.com/linlinjava/litemall)
* [GitHub](https://github.com/linlinjava/litemall)

## Project Architecture
![](./doc/pics/readme/project-structure.png)

## Technology Stack

> 1. Spring Boot
> 2. Vue
> 3. WeChat mini-program

![](doc/pics/readme/technology-stack.png)

## Features

### Mini Mall Features

# Homepage
# Topic list and topic details
# Category list and category details
# Brand list and brand details
# New releases and popular recommendations
# Coupon list and coupon selection
# Group buying
# Search
# Product details, product reviews, product sharing
# Shopping cart
# Place order
# Order list, order details, order after-sales
# Address management, favorites, browsing history, feedback
# Customer service

### Admin Platform Features

# Member management
# Mall management
# Product management
# Promotion management
# System management
# Configuration management
# Statistics and reporting

## Quick Start

1. Set up the minimum development environment:
    * [MySQL](https://dev.mysql.com/downloads/mysql/)
    * [JDK 1.8 or higher](http://www.oracle.com/technetwork/java/javase/overview/index.html)
    * [Maven](https://maven.apache.org/download.cgi)
    * [Node.js](https://nodejs.org/en/download/)
    * [WeChat Developer Tools](https://developers.weixin.qq.com/miniprogram/dev/devtools/download.html)
    
2. Import database files in `litemall-db/sql` in the following order:
    * `litemall_schema.sql`
    * `litemall_table.sql`
    * `litemall_data.sql`

3. Start backend services for both the mini-mall and the admin panel:

    Open the command line and enter the following commands:
    ```bash
    cd litemall
    mvn install
    mvn clean package
    java -Dfile.encoding=UTF-8 -jar litemall-all/target/litemall-all-0.1.0-exec.jar
    ```
    
4. Start the admin panel frontend:

    Open the command line and enter:
    
    # cd litemall/litemall-admin
    # npm install --registry=https://registry.npm.taobao.org
    # npm run dev
   
    Then, open your browser and go to `http://localhost:9527` to access the admin login page.
    
5. Start the mini-mall frontend:
   
   There are two mini-mall frontend options: `litemall-wx` and `renard-wx`, which developers can import and test separately:
   
   1. Import the `litemall-wx` project into the WeChat Developer Tools.
   2. In project settings, enable "Do not validate legitimate domains, web-view (business domains), TLS versions, and HTTPS certificates."
   3. Click "Compile" to preview the project in the WeChat Developer Tools.
   4. Alternatively, click "Preview," then scan to log in with your phone (ensure that debugging is enabled on the phone).
      
   **Note**:
   > This is a basic startup method. WeChat login, WeChat payment, and other features require additional setup by the developer. For more detailed instructions, see the [documentation](https://linlinjava.gitbook.io/litemall/project).

6. Start the Lite Mall frontend:

    Open the command line and enter:
    ```bash
    cd litemall/litemall-vue
    npm install --registry=https://registry.npm.taobao.org
    npm run dev
    ```
    Then, open your browser (using Chrome in mobile mode is recommended) and go to `http://localhost:6255` to access Lite Mall.

    **Note**:
    > Features are currently unstable as this version is still under development.
        
## Development Roadmap

Current version: [v1.8.0](https://linlinjava.gitbook.io/litemall/changelog)

The project is still in development, and there are many areas for improvement. Below is the current roadmap:

### V 1.0.0 Goals:

1. Complete the optimization and improvement of mini-mall (except for certain features like coupons).
2. Implement CRUD operations for all tables in the admin panel.
3. Add parameter validation in backend services.

### V 2.0.0 Goals:

1. Complete all basic business functions for mini-mall and admin panel.
2. Add statistics, logging, and permission features in the admin panel.
3. Optimize business and detail code.
4. Develop Lite Mall.

### V 3.0.0 Goals:

1. Add auxiliary functions to the admin panel.
2. Enhance security and configuration features in backend services.
3. Implement caching and optimize performance.

## Warning

> 1. This project is for learning and practice purposes only.
> 2. The project is not fully developed and is still in progress; use at your own risk.
> 3. The source code is open-source under the [MIT](./LICENSE) license. Documentation is licensed under the [Creative Commons Attribution-NoDerivatives 4.0 International License](https://creativecommons.org/licenses/by-nd/4.0/deed.en).

## Acknowledgments

This project is based on or references the following projects:

1. [nideshop-mini-program](https://github.com/tumobi/nideshop-mini-program)

   **Project Description**: An open-source WeChat mini-program mall based on Node.js and MySQL.

   **Reference**:
   
   1. The `litemall` project database is based on the `nideshop-mini-program` database.
   2. The `litemall-wx` module in this project is developed based on `nideshop-mini-program`.

2. [vue-element-admin](https://github.com/PanJiaChen/vue-element-admin)
  
   **Project Description**: An admin integration solution based on Vue and Element.

   **Reference**: The `litemall-admin` module frontend framework in `litemall` was adapted from `vue-element-admin`.

3. [mall-admin-web](https://github.com/macrozheng/mall-admin-web)

   **Project Description**: `mall-admin-web` is a frontend project for an e-commerce admin system based on Vue and Element.

   **Reference**: Some page layouts in `litemall-admin` are based on `mall-admin-web`.

4. [biu](https://github.com/CaiBaoHong/biu)

   **Project Description**: A scaffolding for admin projects based on `vue-element-admin` and Spring Boot, using a front-end and back-end separation.

   **Reference**: The permission management feature in `litemall` was inspired by `biu`.

5. [vant--mobile-mall](https://github.com/qianzhaoy/vant--mobile-mall)

   **Project Description**: A mobile mall based on the Vant component library.

   **Reference**: The `litemall-vue` module in `litemall` was developed based on `vant--mobile-mall`.

## Recommendations

1. [Flutter_Mall](https://github.com/youxinLu/mall)
   
   **Project Description**: Flutter_Mall is an open-source online mall application built with Flutter.
   
2. [Taro_Mall](https://github.com/jiechud/taro-mall)

    **Project Description**: Taro_Mall is a multi-platform online mall application. The backend is developed based on `litemall`, and the frontend is written using the Taro framework.


## Questions

![](doc/pics/readme/qq4.png)

 * If you have questions or suggestions, please use Issues to provide feedback. Include detailed information.
 * Group discussions should focus on development, business, and collaboration topics.
 * If asking questions in the QQ group, please complete the following steps first:
    * Carefully read the project documentation, especially the [**FAQ**](https://linlinjava.gitbook.io/litemall/faq), to see if it resolves your question.
    * Read [How to Ask Questions the Smart Way](https://github.com/ryanhanwu/How-To-Ask-Questions-The-Smart-Way/blob/master/README-zh_CN.md).
    * Search relevant technology using Baidu or Google.
    * Check the official documentation for related technologies, such as the official WeChat mini-program documentation.
    * Debug or analyze your issue as much as possible before asking. When asking, provide detailed error information and your understanding of the problem.

## License

[MIT](https://github.com/linlinjava/litemall/blob/master/LICENSE)  
Copyright (c) 2018-present linlinjava# litemallstore
