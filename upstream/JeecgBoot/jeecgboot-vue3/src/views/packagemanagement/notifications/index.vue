<template><PackagePage :definition="definitions['notifications']" /></template>
<script setup lang="ts">
defineOptions({ name: "Packagenotifications" });
import PackagePage from '../components/PackagePage.vue';
import { definitions } from '../package.data';
</script>
