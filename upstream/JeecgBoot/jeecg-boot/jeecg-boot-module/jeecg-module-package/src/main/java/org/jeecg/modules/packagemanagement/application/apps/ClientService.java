package org.jeecg.modules.packagemanagement.application.apps;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public interface ClientService {

    Object api(HttpServletRequest request);

    Object login(HttpServletRequest request);

    void redirected(HttpServletRequest request, HttpServletResponse response);

    Object getBanner(HttpServletRequest request);

    Object checkUrl(HttpServletRequest request);

    Object dynamicAdj(HttpServletRequest request);

    Object getUserInfo(HttpServletRequest request);

    Object base64Data(HttpServletRequest request);

    Object base64Data2(HttpServletRequest request);

    Object base64Data3(HttpServletRequest request);


    /*新增接口*/

    Object app1(HttpServletRequest request);
//
    Object app2(HttpServletRequest request);

}
