<#-- @ftlvariable name="user" type="com.sbzhouhao.example.domain.User" -->
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="utf-8">
    <title>User details</title>
</head>
<body>
<nav role="navigation">
    <ul>
        <li><a href="/">Home</a></li>
    </ul>
</nav>

<h1>User details</h1>

<p>E-mail: ${user.user.email}</p>

<p>Role: ${user.role}</p>
</body>
</html>