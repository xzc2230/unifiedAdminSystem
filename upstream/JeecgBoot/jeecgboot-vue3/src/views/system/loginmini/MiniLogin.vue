<template>
  <div :class="prefixCls" class="login-background-img">
    <AppLocalePicker class="absolute top-4 right-4 enter-x xl:text-gray-600" :showText="false"/>
    <AppDarkModeToggle class="absolute top-3 right-7 enter-x" />
    <div>
      <div class="aui-content">
        <div class="aui-container">
          <div class="aui-form">
            <div class="aui-image"></div>
            <div class="aui-formBox">
              <div class="aui-formWell">
                <div class="aui-flex aui-form-nav investment_title">
                  <div class="aui-flex-box activeNav on">{{ t('sys.login.signInFormTitle') }}</div>
                </div>
                <div class="aui-form-box">
                  <a-form ref="loginRef" :model="formData" @keyup.enter.native="accountLogin">
                    <div class="aui-account">
                      <div class="aui-inputClear">
                        <i class="icon icon-code"></i>
                        <a-form-item>
                          <a-input class="fix-auto-fill" :placeholder="t('sys.login.userName')" v-model:value="formData.username" />
                        </a-form-item>
                      </div>
                      <div class="aui-inputClear">
                        <i class="icon icon-password"></i>
                        <a-form-item>
                          <a-input class="fix-auto-fill" type="password" :placeholder="t('sys.login.password')" v-model:value="formData.password" />
                        </a-form-item>
                      </div>
                      <div class="aui-inputClear" v-if="showDepart">
                        <i class="icon icon-depart"></i>
                        <div class="JLoginSelectDept">
                          <a-select allow-clear style="width: 100%" :bordered="false" v-model:value="formData.loginOrgCode" :placeholder="t('sys.login.loginOrgCode')">
                            <template #suffixIcon>
                              <Icon icon="ant-design:gold-outline" />
                            </template>
                            <template v-for="depart in departList" :key="depart.orgCode">
                              <a-select-option :value="depart.orgCode">{{ getShortDeptName(depart.label) }}</a-select-option>
                            </template>
                          </a-select>
                        </div>
                      </div>
                      <div class="aui-flex">
                        <div class="aui-flex-box">
                          <div class="aui-choice">
                            <a-checkbox v-model:checked="rememberMe">{{ t('sys.login.rememberMe') }}</a-checkbox>
                          </div>
                        </div>
                      </div>
                    </div>
                  </a-form>
                </div>
                <div class="aui-formButton">
                  <div class="aui-flex">
                    <a-button :loading="loginLoading" class="aui-link-login" type="primary" @click="accountLogin">
                      {{ t('sys.login.loginButton') }}</a-button>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>
<script lang="ts" setup name="login-mini">
  import { computed, onMounted, reactive, ref, toRaw, watch } from 'vue';
  import { useUserStore } from '/@/store/modules/user';
  import { useMessage } from '/@/hooks/web/useMessage';
  import { useI18n } from '/@/hooks/web/useI18n';
  import { AppLocalePicker, AppDarkModeToggle } from '/@/components/Application';
  import { createLocalStorage } from '/@/utils/cache';
  import { useDesign } from "/@/hooks/web/useDesign";
  import { encryptAESCBC } from '/@/utils/cipher';
  import { defHttp } from "@/utils/http/axios";

  const { prefixCls } = useDesign('mini-login');
  const { notification, createMessage } = useMessage();
  const userStore = useUserStore();
  const { t } = useI18n();
  const $ls = createLocalStorage();
  const rememberMe = ref<boolean>(false);
  const REMEMBER_USERNAME_KEY = 'LOGIN_REMEMBER_USERNAME';
  const formData = reactive<any>({
    username: '',
    password: '',
    loginOrgCode: '',
  });
  const loginRef = ref();
  const loginLoading = ref<boolean>(false);
  defineProps({
    sessionTimeout: {
      type: Boolean,
    },
  });
 //**********************查询部门逻辑begin**********************************************
  //用户部门
  const departList = ref([]);
  //部门显示
  const showDepart = computed(()=>{
    return departList.value.length > 1
  })
  //获取部门缩写
  const getShortDeptName = computed(()=>{
    return (deptName) => {
      if (!deptName) return '';
      if (deptName.length > 18) {
        return '...' + deptName.substring(deptName.length-18, deptName.length) ;
      }
      return deptName;
    };
  })
  watch(
      () => formData.username,
      () => {
        formData.loginOrgCode = null;
        departList.value = [];
        checkAccount();
      }
  );
  watch(() => formData.password, () => checkAccount());

  let deptTimer;
  function checkAccount() {
    deptTimer && clearTimeout(deptTimer);
    deptTimer = setTimeout(async () => {
      if (!formData.username || !formData.password) {
        return;
      }
      if (departList.value && departList.value.length == 0) {
        const res = await defHttp.post({
          url: '/sys/loginGetUserDeparts',
          params: {
            username: formData.username,
            password: encryptAESCBC(formData.password),
            loginOrgCode: formData.loginOrgCode,
            loginType: 'account',
          }
        }, {isTransformResponse: false});
        if (res.success && res.result) {
          let {departs,currentOrgCode} = res.result;
          if (departs && departs.length > 0) {
            const hasCurrentDepart = departs.some(item => item.orgCode == currentOrgCode);
            formData.loginOrgCode = hasCurrentDepart?currentOrgCode:null;
            departList.value = departs.map((item) => {
              return {
                label: item.departName,
                value: item.orgCode,
                orgCode: item.orgCode,
                departName: item.departName,
              };
            });
          }
        }
      }
    },500)
  }
 //**********************查询部门逻辑end*************************************************
  async function accountLogin() {
    if (!formData.username) {
      createMessage.warn(t('sys.login.accountPlaceholder'));
      return;
    }
    if (!formData.password) {
      createMessage.warn(t('sys.login.passwordPlaceholder'));
      return;
    }
    try {
      loginLoading.value = true;
      const encryptedPassword = encryptAESCBC(formData.password);
      const { userInfo } = await userStore.login(
        toRaw({
          password: encryptedPassword,
          username: formData.username,
          loginOrgCode: formData.loginOrgCode,
          mode: 'none',
        })
      );
      if (userInfo) {
        notification.success({
          message: t('sys.login.loginSuccessTitle'),
          description: `${t('sys.login.loginSuccessDesc')}: ${userInfo.realname}`,
          duration: 3,
        });
        if (rememberMe.value && formData.username) {
          $ls.set(REMEMBER_USERNAME_KEY, formData.username)
        } else {
          $ls.remove(REMEMBER_USERNAME_KEY)
        }
      }
    } catch (error) {
      notification.error({
        message: t('sys.api.errorTip'),
        description: (error as Error).message || t('sys.login.networkExceptionMsg'),
        duration: 3,
      });
    } finally {
      loginLoading.value = false;
    }
  }

  onMounted(() => {
    const saved = $ls.get(REMEMBER_USERNAME_KEY);
    if (saved) {
      formData.username = saved;
      rememberMe.value = true;
    }
  });
</script>

<style lang="less" scoped>
  @import '/@/assets/loginmini/style/home.less';
  @import '/@/assets/loginmini/style/base.less';

  :deep(.ant-input:focus) {
    box-shadow: none;
  }
  .aui-get-code {
    float: right;
    position: relative;
    z-index: 3;
    background: #ffffff;
    color: #1573e9;
    border-radius: 100px;
    padding: 5px 16px;
    margin: 7px;
    border: 1px solid #1573e9;
    top: 12px;
  }

  .aui-get-code:hover {
    color: #1573e9;
  }

  .code-shape {
    border-color: #dadada !important;
    color: #aaa !important;
  }

  :deep(.jeecg-dark-switch){
    position:absolute;
    margin-right: 10px;
  }
  .aui-link-login{
    height: 42px;
    padding: 10px 15px;
    font-size: 14px;
    border-radius: 8px;
    margin-top: 15px;
    margin-bottom: 8px;
    flex: 1;
    color: #fff;
  }
  .aui-phone-logo{
    position: absolute;
    margin-left: 10px;
    width: 60px;
    top:2px;
    z-index: 4;
  }
  .top-3{
    top: 0.45rem;
  }
  .JLoginSelectDept {
    margin:5px auto;
    :deep(.ant-select-selection-placeholder) {
      font-size: 14px;
      color: #9a9a9a;
    }
  }
</style>

<style lang="less">
@prefix-cls: ~'@{namespace}-mini-login';
@dark-bg: #293146;

html[data-theme='dark'] {
  .@{prefix-cls} {
    background-color: @dark-bg !important;
    background-image: none;

    &::before {
      background-image: url(/@/assets/svg/login-bg-dark.svg);
    }
    .aui-inputClear{
      background-color: #232a3b !important;
    }
    .ant-input,
    .ant-input-password {
      background-color: #232a3b !important;
    }

    .ant-btn:not(.ant-btn-link):not(.ant-btn-primary) {
      border: 1px solid #4a5569 !important;
    }

    &-form {
      background: @dark-bg !important;
    }

    .app-iconify {
      color: #fff !important;
    }
    .aui-inputClear input,.aui-input-line input,.aui-choice{
      color: #c9d1d9 !important;
    }

    .aui-formBox{
      background-color: @dark-bg !important;
    }
    .aui-third-text span{
      background-color: @dark-bg !important;
    }
    .aui-form-nav .aui-flex-box{
      color: #c9d1d9 !important;
    }

    .aui-formButton .aui-linek-code{
      background:  @dark-bg !important;
      color: white !important;
    }
    .aui-code-line{
      border-left: none !important;
    }
    .ant-checkbox-inner,.aui-success h3{
      border-color: #c9d1d9;
    }
    // 代码逻辑说明: 【QQYUN-6363】这个样式代码有问题，不在里面，导致表达式有问题------------
    &-sign-in-way {
      .anticon {
        font-size: 22px !important;
        color: #888 !important;
        cursor: pointer !important;

        &:hover {
          color: @primary-color !important;
        }
      }
    }
  }

  input.fix-auto-fill,
  .fix-auto-fill input {
    -webkit-text-fill-color: #c9d1d9 !important;
    box-shadow: inherit !important;
  }

  .ant-divider-inner-text {
    font-size: 12px !important;
    color: @text-color-secondary !important;
  }
  .aui-third-login a{
    background: transparent;
  }
}
</style>
