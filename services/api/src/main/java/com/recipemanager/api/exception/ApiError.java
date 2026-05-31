package com.recipemanager.api.exception;

// Java record = C# record: immutable, compiler-generates constructor, equals, hashCode, toString.
// Jackson serializes this to: {"error": "...", "status": 404}
public record ApiError(String error, int status) {}
