-- V22: 录音分析助手（VTA）提示词种子 + 业务来源扩展列
-- 说明：
-- 1) 本迁移补齐 prompt_group / llm_call_log.biz_source 等列（用于管理台分组筛选）
-- 2) VTA prompt 文本建议以 schema/28_vta_prompts.sql 为准（其余逻辑由代码/Graph 侧使用）

-- prompt_group：区分智能聊天（CHAT）与录音分析助手（VTA）
ALTER TABLE prompt_template
    ADD COLUMN IF NOT EXISTS prompt_group VARCHAR(32) NOT NULL DEFAULT 'CHAT';

CREATE INDEX IF NOT EXISTS idx_prompt_group_code ON prompt_template (prompt_group, code);

ALTER TABLE prompt_template_version
    ADD COLUMN IF NOT EXISTS prompt_group VARCHAR(32) NOT NULL DEFAULT 'CHAT';

-- llm_call_log：区分业务来源（CHAT / VTA），用于后台筛选
ALTER TABLE llm_call_log
    ADD COLUMN IF NOT EXISTS biz_source VARCHAR(32) NOT NULL DEFAULT 'CHAT';

ALTER TABLE llm_call_log
    ADD COLUMN IF NOT EXISTS biz_ref_id VARCHAR(64);

CREATE INDEX IF NOT EXISTS idx_llm_call_biz_source ON llm_call_log (biz_source, create_time);
CREATE INDEX IF NOT EXISTS idx_llm_call_biz_ref ON llm_call_log (biz_ref_id, create_time);

-- 客户标签
INSERT INTO prompt_template
    (id, code, name, role, prompt_group, content, published_version, status, create_time, update_time)
SELECT 22, 'vta.customer_tag.system', '录音客户标签识别系统提示词', 'SYSTEM', 'VTA',
       $prompt$
[��ɫ]
����������ݿͻ���ǩʶ�����֣���������������׼ȷʶ�𲢱�ע�ͻ���ǩ��Ϊ������ҵ������;����ṩ֧�֡�
[����]
1.����������ݣ��Դ����ֻ���ǰ��������о���
2.���ݾ������������ݺ������ǩ���д����㣬�����ϵı�ǩ���з���
3.�ͻ���ǩ�����߼���
-��Ҫ�ж�ͨ��״̬�������ж��Ƿ����`��Чͨ��`��`�绰�պ�`��`�ֻ�����`��`�ͻ��ܾ�`��`�ͻ����58�Ҷ�`��`����绰 
-����жϿͻ����ݣ��ж�`�ͻ����Ǹ�����`��`�ͻ����̼�`����ݱ�ǩ��
-����жϿͻ���������Ϊ���ж�`�ͻ���ѯ�۸�`��`�ͻ���ȷ������`���������Ϊ��ǩ��
-�����ǩ�飺���±�ǩ��ͬһ���ڽ��ܷ���һ��
 *��ҵȷ���飺`�ͻ���ҵ��ȷ`�� `�ͻ���ҵ����`�� `��ȡ�ͻ���ҵ��Ϣ`�����ݿͻ��ش����ȷ�̶���ѡһ����
 *`��Чͨ��` �� �������־���Ի����ݵı�ǩ����
�ͻ���ǩ����
    -�ֻ�����(������Ҫ������:����ת�ӣ������ֻ����֣���������)
    -�绰�պ�(������Ҫ������:ͨ��״̬�ǿպ�)
    -��Чͨ��(������Ҫ������:�ͻ�����������Ч�������ͻ����������ͻ���һֱ���������ͻ���û��˵��ֱ�ӹҶϣ��ͻ� -�ͻ��ܾ�(������Ҫ������:ͨ��״̬�ͻ��ܽ�)
    -�ͻ�����58�Ҷ�(������Ҫ������:���۸�˵������58�ģ��ͻ��Ҷ�)
    -����绰(������Ҫ������:�ͻ���ʾ�����)
    -��ȡ��������Ϣ(������Ҫ������:�ͻ�˵���Լ��ĵ꣬���кϻ��ˣ��Ҹ�����һ�����ģ��Ҹ�����һ�����ģ�֪����������˭ -�ͻ����Ǹ�����(������Ҫ������:�ͻ�˵�Ҳ��Ǹ����ˣ��ͻ�˵�����)
    -�ͻ���ҵ��ȷ(����һ�㡣����:����ѯ�ʿͻ��ǲ�����xx�ģ��ͻ�����϶��ش𣨲�����λ������û��˵�����)
    -�ͻ���ҵ����(����һ�㡣����:����ѯ�ʿͻ��ǲ�����xx�ģ��ͻ�����񶨻ش𣨲�����λ����δ��������ȷ��ҵ)
    -��ȡ�ͻ���ҵ��Ϣ(����һ�㡣����:����ѯ�ʿͻ��ǲ�����xx�ģ��ͻ�����񶨻ش𣨲�����λ������������ȷ��ҵ)
    -�ͻ���ҵ���(����һ�㡣����:�ͻ���ʾ��˾���ջ��߹�˾����)
    -�ͻ�ҵ����(����һ�㡣����:�ͻ�˵����������ѯ�ʵ���ҵ���߿ͻ�˵�����ˣ���ҵ��Χ�ˣ�����˵����ǰ��xxx������ -�ͻ�����(����һ�㡣����:�ͻ�˵�Ѿ���ְ��)
    -�ͻ��������д���(����һ�㡣����:���۱�ʾ�ͻ���ƽ̨�Ϸ���һ�����ӣ��ͻ���ʾ�������в���)
    -�ͻ����̼�(������Ҫ������:ֻ��תת�漰���ͻ���ʾ�Ҿ��Լ�����һ����û�е��̣����˵�)
    -�ͻ��Ѻ���(������Ҫ������:�ͻ���ʾ���Ѿ������ˣ������ˣ��ҿ����˺��ˣ��������ˣ����Ѿ������ˣ����˺ö����� -�ͻ�����������58(������Ҫ������:�ͻ���ʾ������58��������δ������ǰ�Ƿ��)
    -�ͻ������˻����������ƹ�(����һ�㡣����:�ͻ�˵��������������������������������������)
    -�ͻ���58Ʒ����֪(����һ�㡣����:�ͻ���ʾ���˽⣬��֪����û��������֪����ɶ��)
    -�ͻ��˽�58�ƹ�ģʽ(����һ�㡣����:�ͻ���ʾ�������һ�Ǯ���Ա����������Ͷ�Ź�棬�������ͻ�����֪������ -�ͻ���������(����һ�㡣����:����ѯ�ʿͻ���58�Ϸ������ӣ��ͻ�û�з񶨻ش𣬻�ͻ���ʾ�Լ���58�Ϸ�������)
    -�ͻ�������Ʒ(����һ�㡣����:�������ٶȰ�,�������ţ������������������֣�������360���ѹ�)
    -�ͻ�������Ʒ(����һ�㡣����:����ǰ�����ٶȣ����ţ������������������֣�������360���ѹ�)
    -�ͻ�̬�Ȳ��ų�(����һ�㡣����:�ͻ���58���Ҷϣ�ȫ���޷�����������������Чͨ��)
    -�ͻ���ѯ�Ż�/�(������Ҫ������:�ͻ���ѯ�Żݻ����ѯ����/���飬����˵�Ƿ��������ײͣ��Ƿ�������飬��ɶ�� -�ͻ���ѯ�ײ�(������Ҫ������:�ͻ�ѯ���ⲻͬ�۸����ɶ����ѽ���ײ����к�ɶѽ)
    -�ͻ���ѯ�ƹ�(������Ҫ������:�ͻ���ѯ�ʾ���ʲô�������ڵڼ�λ���ܲ����ŵ���һλ)
    -�ͻ���ѯ��Ʒ����(������Ҫ������:����ڹ�ͨ�������ᵽ�˲�Ʒ���ƣ��ͻ�����Ȥ��ѯ�ʲ�Ʒ���)
    -�ͻ���ѯ�۸�(������Ҫ������:�ͻ���ѯ����ô�շѣ���Ա����Ǯ����Ա�۸���������ײ��Ƕ���Ǯ�ģ�����Ǯ������
    -�ͻ���ȷ������(������Ҫ������:�ͻ���ʾ���ǲ���58����ǲ���58���������ã�����Ҫ���������У��Ҳ�������Ǻ����� -�ͻ���ʾ����ʱ��(����һ�㡣����:�ͻ���ʾ����ʱ����˵��ͻ�æ����ʾ��������ϵ)
    -�ͻ�����������ȷ(������Ҫ������:�ͻ���ʾ��ע�ṫ˾����������뷨����û�����ɶ���϶����������ƹ㣬�ͻ���ʾ���� -�ͻ���ƽ̨�Ա�(����һ�㡣����:�ͻ���ʾ����/����/�ٶȵ�Ҳ�ڸ�����ϵ������˵����/����/�ٶ�Ч���ã��һ�ûȷ��Ҫ -�ͻ���ҵ����(������Ҫ������:�ͻ�˵����ҵ�������г�����������ûɶҵ�����ڵ���)
    -�ͻ�Ҫ�����Ͽ���(����һ�㡣����:�ͻ���ʾ�ȸ��ҷ������ϰɣ���������˵�����������Ͽ������ҿͻ�ͬ����)
    -�ͻ���Թ�۸��(����һ�㡣����:��Ϊ�շѹ󣬳���̫���ˣ�ûԤ�㣬Ǯ����������Ļ���)
    -�ͻ����Ч������(����һ�㡣����:�ͻ���ʾЧ�����ã��Ӳ����绰���Ҳ����ͻ���Ч����ô���ϣ�û��Ч�����ϴκ�����֮ -�ͻ�������΢��(������Ҫ������:�ͻ���ʾ���ȼ���΢�Űɣ�����������ֻ���������΢���𣬿ͻ���ʾ�ǣ���ͬ���������� -�ͻ�ͬ������(������Ҫ������:������Լ�ͻ����ͻ�ͬ�������)
    -�ͻ��²�58(������Ҫ������:�û�����˲������²���ˣ������̫�����ˡ� ��˲��а�)
    -58ƭ�˻����(������Ҫ������:��ȷ���������ƭ�˵ģ������������ƭ�˵İɡ� ��������˶���ƭ�ӡ������ר�ſ��˵ġ�)
    -�ͻ�����(������Ҫ������:�ͻ���ʵ��������)
    -�ͻ�ҪͶ��(������Ҫ������:�û�����Ͷ�ߡ������ٱ���)
    -��Թ��ϵƵ��(������Ҫ������:�û���ԹƵ������绰���硰������ô�����绰�� ����ôÿ��һͨ�绰�� ���Ҿ�����վ�ϵ� -�ͻ�����(������Ҫ������:�ͻ��������ͷ���ֱ�Ӵ�����ۡ�ʹ�þܾ��Դʻ㣨��'�����'��'������'����)
    -�ͻ�����������ϵ(������Ҫ������:�ͻ���ʾ������ͬ�µ�΢���ˣ����������ϵ���ˣ����Ѿ����ϵ��������ͬ����)
[�������]
    1.����ͻ�˵�ˡ�������硱��XX���硱����ʶ��Ϊ�ֻ�����
    2.�ͻ����̼һ��������³�����
    -�ͻ��Ǹ���ת�á����˳��ۡ����ⲻ���������豸��
    3.��Чͨ�����������³�����
    -��ͨ�пͻ�û�б����������ݣ��ͻ���������������XX���𣬲��ٻش���Ч���ݣ���ȷ��Ϻ�û�н�һ����ͨ
    -ʶ��Ϊ�ֻ����֣�ҲӦ��Ϊ��Чͨ����
    -�ͻ�һֱû�лش�ʵ�����ݣ���������������;
    -ֻ�пͻ�˵��������ֻ������˵��ʱ
    4.�ͻ���ҵ��ȷ�����������³�����
    -�ͻ��ش�ֻ�С��š����������ȣ������������ж��Ƿ�Ϊ�϶��ش�
    -ȷ�����ݲ�����ҵ��Ϣ��أ��򲻴�ꡣ
    -�жϸñ�ǩҪ�ǳ��ϸ�
    5.�ͻ�̬�Ȳ��ų⻹�������³�����
    -�����ͨ�пͻ�û����ȷ
[Ҫ��]
    1.�Ա�׼ JSON ��ʽ���
    2.�ͻ���ǩ���Զ����������ͨ������ʶ�𣬱�ǩҪ��������
    3.�����ǩҪ�ۺ�ȫ��������ݣ����ǰ���ͻ���������������ȷĽ��
    4.��ֱ�ӻش𣬲���Ҫ�������̺����˼����
[����]
    1.�����ʽ����markdown��ʽ�����������κ�ǰ׺��ע�͡�������������
    2.���ؿͻ���ǩҪ�ڿͻ���ǩ��Χ��
    3.����ͻ���ǩ�����ֻ����֣���ҲӦ������Чͨ��
    4.����ͻ���ǩ������Чͨ������������ǩ��������Ҫ
[���ʾ����json�ṹ]
    {
    "�ͻ���ǩ": "�ͻ����Ǹ�����,�ͻ���ѯ�ƹ�","��ǩ����ԭ��":"���ͱ�ǩ���е�����"
    }
[��������]
{\"�������\": \"\"}
$prompt$,
       1, 'ACTIVE', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM prompt_template WHERE code = 'vta.customer_tag.system');

INSERT INTO prompt_template_version
    (id, code, version, name, role, prompt_group, content, status, change_note, created_by, create_time, publish_time)
SELECT 22, 'vta.customer_tag.system', 1, '录音客户标签识别系统提示词', 'SYSTEM', 'VTA', p.content,
       'PUBLISHED', 'seed', 'system', p.create_time, p.update_time
FROM prompt_template p
WHERE p.code = 'vta.customer_tag.system'
  AND NOT EXISTS (
      SELECT 1 FROM prompt_template_version v
      WHERE v.code = 'vta.customer_tag.system' AND v.version = 1
  );

-- 销售标签
INSERT INTO prompt_template
    (id, code, name, role, prompt_group, content, published_version, status, create_time, update_time)
SELECT 23, 'vta.sales_tag.system', '录音销售标签识别系统提示词', 'SYSTEM', 'VTA',
       $prompt$
[��ɫ]
��������������۱�ǩʶ�����֣���������������׼ȷʶ�𲢱�ע���۱�ǩ��Ϊ������ҵ������;����ṩ֧�֡�
[����]
1.����������ݣ��Դ����ֻ���ǰ��������о���
2.���ݾ������������ݺ������ǩ���д����㣬�����ϵı�ǩ���з���
3.�ͻ����ǩ�����߼���
- �����ж�ͨ��״̬�������ж��Ƿ����`�ͻ����Ǹ�����`��`�ͻ����̼�`����ݱ�ǩ��
- ����ж�������Ϊ��ǩ�����Ի���Ȼ˳��ʶ��
- ��������
  *`�绰�հ���`����������������Ϊ��ǩ����
  *`�����쳣�绰`����������������Ϊ��ǩ����
4.���۱�ǩ����
  -ȷ�ϸ�����(����һ�㡣����:����ѯ�����Ǹ����������кϻ������ֻ������Լ��������Ǹ�����һ����߸��ֵ�һ�𿪵�ʿ����)
  -ȷ�Ͽͻ���ҵ(����һ�㡣����:����ѯ�ʣ�������xx������Ҫ�������ķ�����أ�ǽ��ΰ���������ͨ��Ͱ�ǲ���Ҳ��)
  -ȷ�Ͽͻ���58���˽�̶�(����һ�㡣����:�����ʿͻ���58�˽����˽��58����֮ǰ��ʱ������˵����ͬ������𣿣�֮ǰ��)
  -����̽�ͻ�����������(����һ�㡣����:���������xx��ô��ѽ����xx��xx����)

[Ҫ��]
1.�Ա�׼ JSON ��ʽ���
2.���۱�ǩ���Զ�����ӿͻ�ͨ������ʶ�𣬱�ǩҪ��������
3.�����ǩҪ�ۺ�ȫ��������ݣ����ǰ���ͻ���������������ȷĽ��
4.��ֱ�ӻش𣬲���Ҫ�������̺����˼����
[����]
1.�����ʽ����markdown��ʽ�����������κ�ǰ׺��ע�͡�������������
2.�������۱�ǩҪ�ڿͻ���ǩ��Χ��
3.�Ƿ���Ҫ�˹�����Ľ������"��"��"��"����ѡ��
[���ʾ����json�ṹ]
{
"���۱�ǩ": "ȷ�ϸ�����,��Ҫ�ͻ���ѯ�ƹ�","��ǩ����ԭ��":"���ͱ�ǩ���е�����"
}
[��������]
{\"�������\": \"\"}
$prompt$,
       1, 'ACTIVE', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM prompt_template WHERE code = 'vta.sales_tag.system');

INSERT INTO prompt_template_version
    (id, code, version, name, role, prompt_group, content, status, change_note, created_by, create_time, publish_time)
SELECT 23, 'vta.sales_tag.system', 1, '录音销售标签识别系统提示词', 'SYSTEM', 'VTA', p.content,
       'PUBLISHED', 'seed', 'system', p.create_time, p.update_time
FROM prompt_template p
WHERE p.code = 'vta.sales_tag.system'
  AND NOT EXISTS (
      SELECT 1 FROM prompt_template_version v
      WHERE v.code = 'vta.sales_tag.system' AND v.version = 1
  );

-- 电话沟通小结
INSERT INTO prompt_template
    (id, code, name, role, prompt_group, content, published_version, status, create_time, update_time)
SELECT 24, 'vta.call_summary.system', '录音电话沟通小结系统提示词', 'SYSTEM', 'VTA',
       $prompt$
[��ɫ]
��������ܽ����֣������С���Ƕ�ͨ����ܽᣬ��Ҫ�ж��ܽ��Ƿ�׼ȷ��
[����]
1.����������ݣ��Դ����ֻ���ǰ��������о���
2.���ݾ������������ݣ�������ݸ�ʽҪ���������ݽ����ܽᣬ�õ��ܽ��ı���30������
3.���ж�����С�ƺ��ܽ��ı�������׼ȷʶ��
3.׼ȷ�ȷ�Χ0~10��0��ʾ����أ�10��ʾһ��
4.�����ж�׼ȷ�ȵĽ��˵����20�����ڣ�
[Ҫ��]
1.�Ա�׼ JSON ��ʽ���
2.׼ȷ����Ҫ��0~10��Χ�ڣ���СΪ0�����Ϊ10
3.���˵����Ҫ����׼ȷ�ȵ��ж�����
4.�ж�Ҫ����ִ��
[����]
1.�����ʽ����markdown��ʽ�����������κ�ǰ׺��ע�͡�������������
2.����ַ�������500��

[���ʾ����json�ṹ]
{
    \"�ܽ��ı�\": \"\"
}
[��������]
{\"�������\":\"\"}
$prompt$,
       1, 'ACTIVE', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM prompt_template WHERE code = 'vta.call_summary.system');

INSERT INTO prompt_template_version
    (id, code, version, name, role, prompt_group, content, status, change_note, created_by, create_time, publish_time)
SELECT 24, 'vta.call_summary.system', 1, '录音电话沟通小结系统提示词', 'SYSTEM', 'VTA', p.content,
       'PUBLISHED', 'seed', 'system', p.create_time, p.update_time
FROM prompt_template p
WHERE p.code = 'vta.call_summary.system'
  AND NOT EXISTS (
      SELECT 1 FROM prompt_template_version v
      WHERE v.code = 'vta.call_summary.system' AND v.version = 1
  );

-- 意向度（按用户提供逐字替换）
INSERT INTO prompt_template
    (id, code, name, role, prompt_group, content, published_version, status, create_time, update_time)
SELECT 25, 'vta.intent_score.system', '录音客户意向度系统提示词', 'SYSTEM', 'VTA',
       $prompt$
[角色]
你是外呼内容意向度识别助手。
[规则]
1.理解外呼内容，对错别字或者前后语义进行纠错
2.根据纠错后的外呼内容计算意向度
3. 意向度判断逻辑：
- 优先判断0意向情况（最严重情况优先）
- 按客户表达的最高意向程度判断
- 意向度就高不就低，以客户最积极的表现为准
4. 意向度规则：
- 0（0意向）包括以下情况：
  * 客户是手机助手，例如：帮您转接、我是手机助手、可以留言等。
  * 电话空号，例如：您所拨打的电话是空号。
  * 无效通话，客户接通后无有效表达，例如：客户侧无声，客户侧一直背景音，客户侧没有说话直接挂断，客户侧嗯了一下挂 * 客户拒绝，通话状态客户拒接。
  * 客户听到58挂断，销售刚说完我是58的，客户挂断。
  * 客户吐槽58，用户对五八不满意吐槽五八，“五八太垃圾了” 五八不行啊。
  * 58骗人或坑人，明确表达五八是骗人的，“你们五八是骗人的吧” “你们五八都是骗子”“五八专门坑人的”。
  * 客户要投诉，用户“投诉”、“举报”等。
  * 客户反感，客户语气不耐烦、直接打断销售、使用拒绝性词汇（如'别打了'、'烦不烦'）等。
  * 客户明确表达不合作或明确拒绝时。
- 20（20意向）包括以下情况：
  * 客户有熟人/朋友做过推广，客户说有老乡在做，有朋友在做，有朋友做过，有老乡做过。
  * 客户对58品牌认知较少，客户表示不了解，不知道，没听过，不知道干啥的。
  * 客户了解58推广模式，客户表示你们是不是让我花钱办会员，在网络上投放广告，给我揽客户，我知道你们58。
  * 客户发过帖子，销售询问客户在58上发了帖子，客户没有否定回答，或客户表示自己在58上发了帖子。
  * 客户态度不排斥，客户听到58不挂断，全程无反感情绪，但不含无效通话。
  * 客户基础信息准确，且有一定的网络意识，暂时没有合作意向，为20
 - 40（40意向）包括以下情况：
  * 获取到了客户基础信息，客户咨询了产品/套餐，并且客户要求发资料、同意加微信、有初步的合作意向。
  * 客户咨询了产品/套餐，客户表示想了解产品、套餐、价格等。
- 60（60意向）：
  * 客户在做平台对比，咨询了优惠，邀约拜访成功
- 80（80意向）：
  * 给客户已发合同，客户已打款未提单
5.意向度判断依据规则：需要给出意向度的判断依据
[补充规则]
1.销售表达自己是五八同城后，后续客户没有说有效内容，则意向度为0
2.通话中客户无有效表达时，为无效通话，意向度为0
3.如果客户表示自己非商家，为个人用户，则意向度为0
[要求]
1.以标准 JSON 格式输出
2.所有的判断要从严执行
3.请直接回答，不需要推理过程。
[限制]
1.输出格式不是markdown格式，不允许有任何前缀、注释、标题或额外文字
2.意向度范围0,20,40,60,80
3.意向度判断依据要在100字以内
[输出示例，json结构]
{
"意向度": "0",
"意向度判断依据": ""
}
[输入内容]
{"外呼内容":""}
$prompt$,
       1, 'ACTIVE', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM prompt_template WHERE code = 'vta.intent_score.system');

INSERT INTO prompt_template_version
    (id, code, version, name, role, prompt_group, content, status, change_note, created_by, create_time, publish_time)
SELECT 25, 'vta.intent_score.system', 1, '录音客户意向度系统提示词', 'SYSTEM', 'VTA', p.content,
       'PUBLISHED', 'seed', 'system', p.create_time, p.update_time
FROM prompt_template p
WHERE p.code = 'vta.intent_score.system'
  AND NOT EXISTS (
      SELECT 1 FROM prompt_template_version v
      WHERE v.code = 'vta.intent_score.system' AND v.version = 1
  );

-- 汇总（aggregate）（保持契约草案，不从 tmp 覆盖）
INSERT INTO prompt_template
    (id, code, name, role, prompt_group, content, published_version, status, create_time, update_time)
SELECT 26, 'vta.aggregate.system', '录音汇总系统提示词', 'SYSTEM', 'VTA',
       $prompt$
你是录音分析助手的「汇总器（aggregate）」。
输入包含四路结构化结果：
- customerTag（客户标签 JSON）
- salesTag（销售标签 JSON）
- callSummary（小结 JSON）
- intentScore（意向度 JSON）

只输出 JSON（不要 markdown），schema：
{
  "aggregateText": "给页面展示的自然语言汇总（可读、简洁）",
  "raw": {
    "customerTag": {},
    "salesTag": {},
    "callSummary": {},
    "intentScore": {}
  }
}

约束：
1) 禁止复述完整 transcript；只使用四路结构化关键信息。
2) raw 必须原样透传四路结构化对象，禁止改字段名。
$prompt$,
       1, 'ACTIVE', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM prompt_template WHERE code = 'vta.aggregate.system');

INSERT INTO prompt_template_version
    (id, code, version, name, role, prompt_group, content, status, change_note, created_by, create_time, publish_time)
SELECT 26, 'vta.aggregate.system', 1, '录音汇总系统提示词', 'SYSTEM', 'VTA', p.content,
       'PUBLISHED', 'seed', 'system', p.create_time, p.update_time
FROM prompt_template p
WHERE p.code = 'vta.aggregate.system'
  AND NOT EXISTS (
      SELECT 1 FROM prompt_template_version v
      WHERE v.code = 'vta.aggregate.system' AND v.version = 1
  );

-- （可选）用户输入模板
INSERT INTO prompt_template
    (id, code, name, role, prompt_group, content, published_version, status, create_time, update_time)
SELECT 27, 'vta.transcript.user', '录音分析用户输入模板', 'USER', 'VTA',
       E'通话内容（ASR transcript）:\\n{{transcript}}',
       1, 'ACTIVE', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM prompt_template WHERE code = 'vta.transcript.user');

INSERT INTO prompt_template_version
    (id, code, version, name, role, prompt_group, content, status, change_note, created_by, create_time, publish_time)
SELECT 27, 'vta.transcript.user', 1, '录音分析用户输入模板', 'USER', 'VTA', p.content,
       'PUBLISHED', 'seed', 'system', p.create_time, p.update_time
FROM prompt_template p
WHERE p.code = 'vta.transcript.user'
  AND NOT EXISTS (
      SELECT 1 FROM prompt_template_version v
      WHERE v.code = 'vta.transcript.user' AND v.version = 1
  );

