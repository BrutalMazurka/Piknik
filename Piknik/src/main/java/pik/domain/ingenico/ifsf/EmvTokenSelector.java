package pik.domain.ingenico.ifsf;

import com.google.common.collect.ImmutableList;
import epis5.ingenicoifsf.prot.xml.IfsfPrivateData;
import jCommons.utils.StringUtils;

import java.util.ArrayList;
import java.util.List;

public class EmvTokenSelector {
    //TOKEN ... je primární bankovní token, který zajišťuje párování vůči systémům banky. Denylist/stoplist potom obsahuje právě tyto tokeny. Ve Variable Fare je povinný.
    //TOKENH1   ... Token Kordis

    public static final String DUK_TOKEN_PD_KEY = "TOKEN";

    private final IfsfPrivateData privateData;

    public EmvTokenSelector(IfsfPrivateData privateData) {
        this.privateData = privateData;
    }

    public static List<String> getSupportedTokenNames() {
        List<String> list = new ArrayList<>();
        list.add(DUK_TOKEN_PD_KEY);
        return ImmutableList.copyOf(list);
    }

    public String getPrivateDataAsString() {
        return privateData.asAuditLogText();
    }

    public String getDukToken() {
        String valueForKey = privateData.get(DUK_TOKEN_PD_KEY, "");
        if (!StringUtils.isNullOrBlank(valueForKey)) {
            return valueForKey;
        }

        String token = "";
        for (String key : privateData.getKeys()) {
            if (!IfsfPrivateData.isTokenKey(key)) {
                continue;
            }
            String tokenStr = privateData.get(key);
            if (tokenStr == null) {
                continue;
            }
            token = tokenStr;
            break;
        }

        return token;
    }
}
