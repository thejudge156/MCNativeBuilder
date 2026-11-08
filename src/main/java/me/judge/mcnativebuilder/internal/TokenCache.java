package me.judge.mcnativebuilder.internal;

import com.microsoft.aad.msal4j.ITokenCacheAccessAspect;
import com.microsoft.aad.msal4j.ITokenCacheAccessContext;

import java.io.*;

public class TokenCache implements ITokenCacheAccessAspect {
    String data;
    File cacheFile;

    public TokenCache(String cachePath) throws IOException {
        this.cacheFile = new File(cachePath, "auth_cache.json");
        if (!cacheFile.exists()) {
            this.cacheFile.getParentFile().mkdirs();
            this.cacheFile.createNewFile();
        }

        BufferedReader reader = new BufferedReader(new FileReader(cacheFile.getPath()));
        StringBuilder sb = new StringBuilder();
        reader.lines().forEach(sb::append);
        reader.close();
        this.data = sb.toString();
    }

    @Override
    public void beforeCacheAccess(ITokenCacheAccessContext iTokenCacheAccessContext) {
        iTokenCacheAccessContext.tokenCache().deserialize(data);
    }

    @Override
    public void afterCacheAccess(ITokenCacheAccessContext iTokenCacheAccessContext) {
        data = iTokenCacheAccessContext.tokenCache().serialize();
        try {
            BufferedWriter writer = new BufferedWriter(new FileWriter(cacheFile));
            writer.write(data);
            writer.flush();
            writer.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
