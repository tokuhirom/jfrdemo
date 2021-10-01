<!doctype html>
<html>
<head>
    <title>JFR sample app</title>
</head>
<body>
<h1>JFR configurations</h1>

<#list keys as key>
    <#if key.second>
        <a href="/sample?key=${key.first}">${key.first}</a><br>
    <#else>
        ${key}<br>
    </#if>
</#list>
</body>
</html>